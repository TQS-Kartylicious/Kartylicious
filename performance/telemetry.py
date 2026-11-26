import json
import re
import subprocess
import threading
import time
from datetime import datetime, timezone


def measure(compose, get, token):
    """Compare broker observations and the existing Actuator timer over the same window."""
    published = set()
    ready = threading.Event()
    stats = {"invalid_sample_count": 0}
    duration = 60
    polling = 1
    drain = 5

    def collect(stream):
        for line in stream:
            try:
                topic, payload = line.split(" ", 1)
                timestamp = datetime.fromisoformat(json.loads(payload)["recordedAt"]).timestamp()
                published.add((topic, timestamp))
                ready.set()
            except (ValueError, KeyError, TypeError):
                stats["invalid_sample_count"] += 1

    def timer():
        before = time.time()
        response = get("/actuator/metrics/fleet.telemetry.processing.lag", token=token)
        after = time.time()
        values = {entry["statistic"]: entry["value"] for entry in response["measurements"]}
        if response["baseUnit"] != "seconds":
            raise RuntimeError("Unexpected telemetry timer unit")
        return (before + after) / 2, values

    command = ("exec", "-T", "mqtt", "mosquitto_sub", "-q", "1", "-t",
               "kartylicious/karts/+/telemetry", "-F", "%t %p")
    with subprocess.Popen(compose(*command), stdout=subprocess.PIPE,
                          text=True, env=compose.env) as monitor:
        reader = threading.Thread(target=collect, args=(monitor.stdout,), daemon=True)
        reader.start()
        try:
            if not ready.wait(5):
                raise RuntimeError("The isolated broker did not publish a fleet observation")
            time.sleep(10)
            start, initial = timer()
            schedule = time.monotonic()
            for tick in range(polling, duration + drain + 1, polling):
                time.sleep(max(0, schedule + tick - time.monotonic()))
                timestamp, sample = timer()
                stats.setdefault("sampled_max_seconds", []).append(sample["MAX"])
                if tick == duration:
                    end, final = timestamp, sample
            if monitor.poll() is not None:
                raise RuntimeError("The MQTT observer stopped before the measurement ended")
        finally:
            monitor.terminate()
            reader.join(timeout=5)
            if reader.is_alive():
                monitor.kill()
                reader.join(timeout=5)

    elapsed = end - start
    publications = sum(start <= timestamp < end for _, timestamp in published)
    processed = int(final["COUNT"] - initial["COUNT"])
    total_lag = final["TOTAL_TIME"] - initial["TOTAL_TIME"]
    observed_max = max(stats["sampled_max_seconds"])
    since = datetime.fromtimestamp(start, timezone.utc).isoformat()
    logs = compose.output("logs", "--no-color", "--since", since, "backend")
    failed_transactions = len(re.findall("Fleet telemetry transaction failed", logs))
    broker_rate = publications / elapsed
    return {
        "duration_seconds": elapsed,
        "configured_readings_per_second": 10,
        "published_observations": publications,
        "published_per_second": broker_rate,
        "processed_observations": processed,
        "processed_per_second": processed / elapsed,
        "mean_lag_seconds": total_lag / processed if processed > 0 else None,
        "highest_sampled_rolling_max_seconds": observed_max,
        "rolling_max_sample_interval_seconds": polling,
        "rolling_max_samples_seconds": stats["sampled_max_seconds"],
        "post_window_sampling_seconds": drain,
        "publication_minus_processing_count": publications - processed,
        "backend_transaction_failures_logged": failed_transactions,
        "invalid_broker_payloads_observed": stats["invalid_sample_count"],
        "passed": (round(broker_rate, 1) == 10.0 and abs(publications - processed) <= 1
                   and processed > 0 and observed_max < 5 and total_lag / processed < 5
                   and failed_transactions == 0 and stats["invalid_sample_count"] == 0),
    }
