#!/usr/bin/env python3
"""Run workloads against a new local Docker project and write aggregate evidence only."""

import argparse
import json
import os
import re
import secrets
import shutil
import subprocess
import tempfile
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener
from uuid import UUID

from telemetry import measure


HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
DOCKER = ["docker", "--host", "unix:///var/run/docker.sock"]
SUITES = ("telemetry", "booking", "discovery", "booking-contention", "return-contention", "health")
DB = "kartylicious_perf"
MARKER = "kartylicious-disposable-performance"


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


class Compose:
    def __init__(self, directory, project):
        self.project = project
        # Ignore shell overrides, Docker contexts and the repository's .env file.
        self.env = {"PATH": os.environ["PATH"], "COMPOSE_DISABLE_ENV_FILE": "true"}
        self.base = DOCKER + [
            "compose", "--project-directory", str(directory), "--project-name", project,
            "--env-file", str(directory / "synthetic.env"), "-f", str(directory / "docker-compose.yml"),
            "-f", str(directory / "performance/compose.yml"),
        ]

    def __call__(self, *args):
        return self.base + list(args)

    def output(self, *args):
        return subprocess.check_output(self(*args), text=True, env=self.env).strip()

    def run(self, *args, **kwargs):
        return subprocess.run(self(*args), check=True, text=True, env=self.env, **kwargs)

    def sql(self, statement, *, write=False):
        mode = "off" if write else "on"
        result = self.run(
            "exec", "-T", "-e", f"PGOPTIONS=-c default_transaction_read_only={mode}", "db",
            "psql", "-X", "-qAt", "-v", "ON_ERROR_STOP=1", "-U", DB, "-d", DB,
            input=statement, stdout=subprocess.PIPE,
        )
        return result.stdout.strip()

    def check_identity(self):
        db_id = self.output("ps", "-q", "db")
        backend_id = self.output("ps", "-q", "backend")
        containers = subprocess.check_output(DOCKER + ["inspect", db_id, backend_id],
                                             text=True, env=self.env)
        db, backend = json.loads(containers)
        for container, service in ((db, "db"), (backend, "backend")):
            labels = container["Config"]["Labels"]
            require(labels["com.docker.compose.project"] == self.project and
                    labels["com.docker.compose.service"] == service,
                    "Refusing to use a container outside this disposable project")
        mounts = db["Mounts"]
        require(any(mount.get("Name") == f"{self.project}_pgdata" and
                    mount["Destination"] == "/var/lib/postgresql/data" for mount in mounts),
                "Refusing to use a database without this project's fresh volume")
        require(not any((db["HostConfig"]["PortBindings"] or {}).values()),
                "The performance database must have no published ports")
        require(self.sql("SELECT current_database() || '|' || shobj_description(oid, 'pg_database') "
                         "FROM pg_database WHERE datname = current_database();")
                == f"{DB}|{MARKER}", "Missing disposable performance database marker")
        bindings = backend["NetworkSettings"]["Ports"]["8080/tcp"]
        require(len(bindings) == 1 and bindings[0]["HostIp"] == "127.0.0.1",
                "The performance backend must bind only to loopback")
        port = bindings[0]["HostPort"]
        require(re.fullmatch(r"[0-9]{1,5}", port), "Invalid loopback binding")
        return "http://127.0.0.1:" + port


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def api(base, path, body=None, token=None, expected=200):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = Request(base + path, data=json.dumps(body).encode() if body is not None else None,
                      headers=headers)
    try:
        with build_opener(ProxyHandler({}), NoRedirect()).open(request, timeout=5) as response:
            require(response.status == expected, f"{path} returned HTTP {response.status}")
            return json.load(response)
    except HTTPError as error:
        raise RuntimeError(f"{path} returned HTTP {error.code}") from None


def wait_for_health(base):
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        try:
            if api(base, "/actuator/health")["status"] == "UP":
                return
        except (OSError, RuntimeError):
            pass
        time.sleep(1)
    raise RuntimeError("Isolated backend did not become healthy")


def login_accounts(base, count):
    tokens = []
    for index in range(count):
        name = f"synthetic-perf-{index + 1}"
        email = f"{name}@example.test"
        password = secrets.token_urlsafe(24)
        api(base, "/api/registration", {"username": name, "email": email, "password": password},
            expected=201)
        token = api(base, "/api/auth/login", {"user": email, "password": password})["token"]
        require(isinstance(token, str) and token, "Login did not return a token")
        tokens.append(token)
    return tokens


def data_for(tokens):
    starts_at = datetime.now(timezone.utc) + timedelta(minutes=10)
    return {
        "tokens": tokens,
        "window": {"startsAt": starts_at.isoformat(),
                   "endsAt": (starts_at + timedelta(minutes=10)).isoformat()},
        "hubs": ["central-square", "riverside-park", "university-gardens"] +
                [f"perf-hub-{index}" for index in range(1, 6)],
        "karts": [{"kartId": f"perf-kart-{index}", "hubId": f"perf-hub-{(index - 1) // 10 + 1}"}
                  for index in range(1, 51)],
    }


def booking_fixture(base, data):
    response = api(base, "/api/bookings",
                   {"hubId": "perf-hub-5", "kartId": "perf-conflict", **data["window"]},
                   data["tokens"][50])
    require(response["status"] == "ACTIVE", "The booking workload conflict fixture is unavailable")


def return_fixture(compose, base, data):
    data["rides"] = [str(UUID(api(base, "/api/rides", kart, token)["id"]))
                     for kart, token in zip(data["karts"][:20], data["tokens"], strict=False)]
    # Trusted arrival fixtures supply telemetry only; ride start and completion use the real API.
    compose.sql("""
        UPDATE karts SET telemetry_hub_id = 'perf-return', telemetry_recorded_at = now(),
          locked = false WHERE id IN (SELECT kart_id FROM rides WHERE status = 'ACTIVE')
          AND id LIKE 'perf-kart-%';
        """, write=True)
    require(compose.sql("""
        SELECT (SELECT count(*) FROM rides r JOIN karts k ON r.kart_id = k.id
                JOIN payment_authorizations p ON p.operation_id = r.id AND p.user_id = r.user_id
                WHERE r.status = 'ACTIVE' AND r.payment_authorization_id = p.operation_id
                  AND p.status = 'AUTHORIZED' AND k.status = 'IN_USE'
                  AND k.telemetry_hub_id = 'perf-return' AND k.telemetry_recorded_at >= r.started_at)
               || '|' ||
               (SELECT h.capacity - count(k.id) FROM hubs h
                LEFT JOIN karts k ON k.hub_id = h.id AND k.status <> 'IN_USE'
                WHERE h.id = 'perf-return' GROUP BY h.capacity);
        """) == "20|1", "Expected 20 eligible rides and exactly one free destination space")


def booking_invariants(compose, baseline):
    result = json.loads(compose.sql("""
        WITH target AS (SELECT * FROM bookings WHERE kart_id = 'perf-race')
        SELECT json_build_object(
          'persisted_bookings', (SELECT count(*) FROM target),
          'active_bookings', (SELECT count(*) FROM target WHERE status = 'ACTIVE'),
          'overlapping_pairs', (SELECT count(*) FROM target a JOIN target b ON a.id < b.id
                                WHERE a.starts_at < b.ends_at AND b.starts_at < a.ends_at),
          'new_authorizations', (SELECT count(*) FROM payment_authorizations) - :baseline,
          'authorized_winning_booking', (SELECT count(*) FROM target b JOIN payment_authorizations p
                                         ON p.operation_id = b.id AND p.user_id = b.user_id
                                         WHERE p.status = 'AUTHORIZED'));
        """.replace(":baseline", str(baseline))))
    return result, result == {
        "persisted_bookings": 1, "active_bookings": 1, "overlapping_pairs": 0,
        "new_authorizations": 1, "authorized_winning_booking": 1,
    }


def return_invariants(compose):
    result = json.loads(compose.sql("""
        SELECT json_build_object(
          'capacity', (SELECT capacity FROM hubs WHERE id = 'perf-return'),
          'occupied', (SELECT count(*) FROM karts WHERE hub_id = 'perf-return' AND status <> 'IN_USE'),
          'completed_at_destination', (SELECT count(*) FROM rides r JOIN karts k ON r.kart_id = k.id
              WHERE r.status = 'COMPLETED' AND r.end_hub_id = 'perf-return'
                AND k.hub_id = 'perf-return' AND k.locked AND k.status = 'AVAILABLE'
                AND r.ended_at IS NOT NULL AND r.final_charge IS NOT NULL),
          'active_uncharged', (SELECT count(*) FROM rides r
              JOIN payment_authorizations p ON p.operation_id = r.payment_authorization_id
              JOIN karts k ON k.id = r.kart_id WHERE r.status = 'ACTIVE' AND p.status = 'AUTHORIZED'
                AND r.end_hub_id IS NULL AND r.ended_at IS NULL AND r.final_charge IS NULL
                AND k.status = 'IN_USE' AND k.hub_id = r.hub_id),
          'settlement_count', (SELECT count(*) FROM payment_settlements),
          'correct_settlement', (SELECT count(*) FROM payment_settlements s
              JOIN rides r ON r.id = s.ride_id
              JOIN payment_authorizations p ON p.operation_id = s.authorization_id
              WHERE r.status = 'COMPLETED' AND r.payment_authorization_id = p.operation_id
                AND p.status = 'SETTLED' AND s.amount = r.final_charge));
        """))
    return result, result == {
        "capacity": 2, "occupied": 2, "completed_at_destination": 1,
        "active_uncharged": 19, "settlement_count": 1, "correct_settlement": 1,
    }


def write_json(path, data):
    path.write_text(json.dumps(data, indent=2) + "\n")


def k6(compose, stage, suite, data):
    input_file = stage / "performance/input.json"
    input_file.touch(mode=0o600, exist_ok=True)
    write_json(input_file, data)
    script = "contention.js" if suite.endswith("-contention") else "load.js"
    code = subprocess.run(compose("run", "--rm", "--no-deps", "-T", "--user",
                                  f"{os.getuid()}:{os.getgid()}", "-e", f"SUITE={suite}", "k6",
                                  "run", "--quiet", f"/performance/{script}"),
                          env=compose.env).returncode
    evidence = stage / "evidence" / f"{suite}.json"
    require(evidence.is_file(), f"k6 produced no {suite} summary (exit {code})")
    summary = json.loads(evidence.read_text())
    summary["k6_exit_code"] = code
    return summary


def stage_sources(stage):
    for path in ("docker-compose.yml", "backend/Dockerfile", "backend/pom.xml", "backend/mvnw",
                 "fleet-simulator/Dockerfile", "fleet-simulator/mosquitto.conf",
                 "fleet-simulator/simulator.py", "performance/compose.yml", "performance/init.sql",
                 "performance/common.js", "performance/load.js", "performance/contention.js"):
        destination = stage / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT / path, destination)
    for path in ("backend/.mvn", "backend/src"):
        shutil.copytree(ROOT / path, stage / path, ignore=shutil.ignore_patterns(".env*", "*.pem"))
    (stage / "evidence").mkdir()
    env_file = stage / "synthetic.env"
    env_file.touch(mode=0o600)
    env_file.write_text(f"POSTGRES_DB={DB}\nPOSTGRES_USER={DB}\n"
                        f"POSTGRES_PASSWORD={secrets.token_hex(24)}\n"
                        f"JWT_SECRET={secrets.token_hex(32)}\n"
                        "FLEET_SIMULATOR_RATE=10\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("suite", choices=("all", *SUITES))
    parser.add_argument("--output", type=Path, required=True, help="New directory for sanitized JSON")
    args = parser.parse_args()
    args.output.mkdir(parents=True)
    requested = SUITES if args.suite == "all" else (args.suite,)
    results = {}
    with tempfile.TemporaryDirectory(prefix="karty-performance-", dir="/tmp") as temporary:
        stage = Path(temporary)
        project = "karty-performance-" + secrets.token_hex(6)
        compose = Compose(stage, project)
        stage_sources(stage)
        # An independently generated project name with no resources is required before starting.
        filters = ["--filter", f"label=com.docker.compose.project={project}", "-q"]
        for resource in (["container", "ls", "-a"], ["volume", "ls"], ["network", "ls"]):
            matches = subprocess.check_output(DOCKER + resource + filters, env=compose.env)
            require(not matches.strip(), "The generated performance project already exists")
        try:
            compose.run("up", "-d", "--build", "--wait", "--wait-timeout", "120",
                        "db", "backend", "mqtt", "simulator")
            base = compose.check_identity()
            wait_for_health(base)
            if requested == ("health",):
                tokens = []
            else:
                tokens = login_accounts(base, 51 if "booking" in requested else
                                        1 if requested == ("telemetry",) else 50)
            data = data_for(tokens)
            staged_fleet = False
            for suite in requested:
                print(f"Running {suite}", flush=True)
                try:
                    if suite == "telemetry":
                        summary = measure(compose, lambda path, **kwargs: api(base, path, **kwargs),
                                          tokens[0])
                    else:
                        if suite != "health" and not staged_fleet:
                            compose.check_identity()
                            compose.sql((HERE / "seed.sql").read_text(), write=True)
                            staged_fleet = True
                        if suite == "booking":
                            booking_fixture(base, data)
                        if suite == "booking-contention":
                            booking_baseline = int(compose.sql(
                                "SELECT count(*) FROM payment_authorizations;"))
                        if suite == "return-contention":
                            compose.check_identity()
                            return_fixture(compose, base, data)
                        summary = k6(compose, stage, suite, data)
                        summary["passed"] = summary["k6_exit_code"] == 0
                        if suite.endswith("-contention"):
                            if suite == "booking-contention":
                                invariants, passed = booking_invariants(compose, booking_baseline)
                            else:
                                invariants, passed = return_invariants(compose)
                            summary["postgres_read_only"] = invariants
                            summary["passed"] = summary["passed"] and passed
                    write_json(args.output / f"{suite}.json", summary)
                    results[suite] = "PASS" if summary["passed"] else "FAIL"
                except (OSError, RuntimeError, subprocess.SubprocessError, ValueError, KeyError) as error:
                    results[suite] = "NOT VERIFIED"
                    print(f"{suite}: {error}", flush=True)
                print(f"{suite}: {results[suite]}", flush=True)
        finally:
            compose.run("down", "--volumes", "--remove-orphans")
    write_json(args.output / "run.json", {"results": results})
    return 0 if results and all(status == "PASS" for status in results.values()) else 1


if __name__ == "__main__":
    raise SystemExit(main())
