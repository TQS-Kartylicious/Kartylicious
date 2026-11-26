# Local SLO verification

From the repository root, use Python 3.10+ and local Docker with Compose 2.24.4+
for the `!override` and `!reset` tags. k6 runs in the pinned Docker image.

```sh
python3 -B performance/run.py all --output performance/results/local
```

The output directory must be new. To run one workload, replace `all` with
`booking`, `discovery`, `booking-contention`, `return-contention`,
`telemetry`, or `health`. The complete run takes roughly five minutes
after image build and initialization. A nonzero exit means at least one
workload missed its criteria or could not run. The runner still attempts the
remaining workloads and saves aggregate JSON for completed measurements.

The runner stages working-tree sources in a temporary directory, ignores the
repository's `.env` and Docker context overrides, and creates a uniquely named
project with fresh synthetic credentials and its own PostgreSQL volume on the
local Docker socket. PostgreSQL and the MQTT broker publish no host ports.
The backend uses a dynamically assigned loopback port. Actuator metrics also
require authentication. k6 targets the backend service on the Compose network.
Before fixture writes, the runner verifies the project, database volume and
disposable database marker. Post-workload assertions use read-only SQL. The
runner removes its project and volume at the end. The JSON evidence contains
aggregate metrics and assertion counts. Test input and bearer tokens stay
in the temporary directory.

| Workload | Dataset and measurement |
| --- | --- |
| Booking | 50 authenticated VUs and 50 dedicated karts across five synthetic hubs. Each VU books a 10-minute future window and cancels successful bookings before reusing that kart. Every twentieth iteration targets an overlapping slot on a separate already reserved kart, producing an expected 409. Each VU pauses 250 ms between iterations. The first 30 seconds are warm-up and the next 60 are measured. The overall and successful POST p95s are both gated at <500 ms. PATCH operations have separate metrics. |
| Discovery | 50 authenticated VUs rotate through the three original seeded hubs and five synthetic hubs, requesting both list and detail on each iteration and checking the response shape. The warm-up, measurement and pacing match booking. Each endpoint has a separate p95 gate of <300 ms. |
| HTTP errors | Both 50-VU workloads count HTTP 5xx below 1% independently. The booking peak metric includes POSTs and cleanup PATCHes, excluding expected booking conflicts. Status-less failures, authentication errors, and other unexpected responses fail their workload independently. |
| Booking contention | 20 distinct accounts dispatch one overlapping booking each for a separate eligible kart at a shared timestamp. Require one success, 19 validated conflicts, one persisted active booking, zero overlapping pairs and one new authorization. |
| Return contention | 20 independent rides started through the normal API with simulated deposit holds. Guarded SQL supplies trusted arrival observations. The two-space destination starts with one kart parked. Each rider submits one return at the shared timestamp. Require one success, 19 validated full-hub conflicts, occupancy equal to capacity, 19 still-active and uncharged rides and exactly one settlement. |
| Telemetry | The original 11-kart simulator sends its configured 10 readings/s to the existing MQTT broker. A separate MQTT subscriber counts unique topic/timestamp pairs on the broker, and authenticated Actuator timer deltas measure processing. Warm-up is 10 seconds followed by 60 seconds of measurement. The rounded observed publication rate must be 10.0/s. Window edges can differ by one observation because publication and processing are asynchronous. |
| Health | One `GET /actuator/health` probe per second for 60 seconds. Require all 60 scheduled probes to be observed and at least 99.5% to return HTTP 200 with `UP`. This measures the local observation window. |

The two sustained HTTP reports show throughput as measured request count
divided by the 60-second sending interval. A request started before the
interval ended can finish afterward. Contention dispatch offsets are captured
immediately before the HTTP call and must fall within 250 ms of the target.
Return contention verifies business logic with fixture-provided telemetry.

For telemetry, the mean lag is the timer's total-time delta divided by its count
delta. The reported maximum is the highest **sampled rolling maximum**. It is
sampled once per second during the window and for five seconds afterward. A
sample may include a warm-up observation still in the rolling window. Broker
and processing counts are aggregate, so their difference is an estimate and
can include work crossing a time boundary. Transaction failures visible in
backend logs are counted separately. Only the telemetry workload exercises
simulator-to-MQTT-to-backend processing.
