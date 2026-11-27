# Local performance evidence, 26 November 2026

The rebuilt working tree ran on an Ubuntu 26.04.1 host with an AMD Ryzen 7
7840HS, 16 logical CPUs and approximately 30 GiB RAM. The run used Docker
Engine 29.1.3, Compose 2.40.3, Java 21, PostgreSQL 16, Mosquitto 2 and k6
1.5.0 on the same host. The backend used its default rental rules, automatic
reservation expiry and the PostgreSQL-backed simulated payment gateway.
Each run used a fresh local project and synthetic accounts.

See the [workload and fixture definitions](README.md) for timing, dataset,
measurement boundaries and database safety. Reproduce from the repository root:

```sh
python3 -B performance/run.py all --output performance/results/reproduction
```

| SLO | Target | Local result | Status |
| --- | --- | --- | --- |
| SLO1 | Booking POST p95 <500 ms at 50 concurrent users | 10.27 ms p95 overall and 8.20 ms p95 for successful POSTs. Of 11,454 measured POSTs (190.9/s), 10,854 succeeded and 600 returned the expected conflict. Zero unexpected responses or timeouts. All 10,854 separate cancellation requests succeeded. | PASS |
| SLO2 | Processing lag <5 s at 10 readings/s | 599 published and 599 processed in 60.02 s (9.98/s at the configured 10/s). Mean 4.29 ms and highest sampled rolling maximum 27.45 ms. Zero broker/processing count difference, invalid payloads or logged transaction failures. | PASS |
| SLO3 | HTTP 5xx rate <1% at 50 concurrent users, excluding expected 4xx business rejections | The booking workload recorded 0/21,708 eligible responses with 5xx, excluding 600 expected conflicts. Booking POST and cancellation PATCH together sent 371.8 requests/s. Hub discovery recorded 0/23,500 with 5xx at 391.7 requests/s. Both workloads passed the threshold with zero network, authentication or other unexpected failures. Wider endpoint coverage under sustained peak load is **NOT VERIFIED**. | PARTIAL |
| SLO4 | p95 <300 ms per read endpoint at 50 concurrent users | Hub list 5.15 ms p95 and hub detail 4.58 ms p95. Each served 11,750 successful responses (195.8/s) with zero unexpected failures. | PASS |
| SLO5 | Zero double-bookings and capacity overflows with at least 20 simultaneous requests | Each 20-account burst had one HTTP 200 and 19 expected 409s. Dispatch offsets were within 1 ms of each target. Read-only SQL confirmed one active reservation and one authorization with zero overlapping pairs. Return occupancy reached 2/2, exactly one settlement was captured, and 19 rides and holds stayed active and uncharged. | PASS |
| SLO6 | Backend availability ≥99.5% during active deployment windows and correct BR15 evaluation against the configured fleet target ≥95% | Local health returned `UP` on 60/60 probes over 60.00 s (100%, zero failures). Deployment-window availability is **NOT VERIFIED**. No BR15 target configuration or SLA evaluation was found in the current application, so fleet evaluation is **NOT IMPLEMENTED**. | PARTIAL |

Telemetry maxima are sampled Micrometer rolling values rather than a
per-observation maximum reconstructed across the run. The SLO5 return test
uses trusted SQL arrival fixtures. The SLO2 run supplies separate evidence
from the live simulator and MQTT path.

The [full run JSON](results/2026-11-26/run.json) and its adjacent files
contain aggregate workload evidence. The initial booking run passed but k6
reported high metric cardinality from unique cancellation URLs. The runner
now omits URL and name tags for that workload. SLO1 and the booking portion
of SLO3 use the [fresh 50-user rerun](results/2026-11-26-booking-recheck/booking.json).

```sh
python3 -B performance/run.py booking --output performance/results/booking-reproduction
```
