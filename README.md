# Kartylicious

## Local application

With the local environment configured from `.env.example`, start the application,
fleet simulator and private Mosquitto broker:

```sh
docker compose up -d --build
```

The simulator publishes 10 observations per second across its configured fleet.
It defaults to all 11 seeded karts with their seeded battery levels, hubs, and
lock states. The seeded in-use kart starts unlocked and outside the hubs. Set
`FLEET_SIMULATOR_FLEET` to a JSON map of seeded kart IDs to initial battery levels,
and `FLEET_SIMULATOR_RATE` to change the total readings per second. MQTT is
available only to services on the internal Compose fleet network.

The topic is `kartylicious/karts/{kartId}/telemetry`. Each JSON message contains
`batteryLevel` (integer 0–100), `hubId` (seeded hub ID or explicit `null` outside
all hubs), `locked` and `criticalFault` (booleans), and `recordedAt` (ISO-8601
instant). The backend rejects future timestamps and invalid readings, and ignores
older readings and duplicate timestamps at microsecond precision. A critical
fault stays latched until an authorized maintenance resolution is implemented.

Start a ride on K-101 in the application, then report its simulated arrival:

```sh
docker compose exec simulator python simulator.py set K-101 --hub central-square
```

End the ride at Central Square after the next reading. The observation comes from
the simulator. Selecting a hub in the rider UI does not publish telemetry.
The same control accepts `--hub outside`, `--battery 19`, `--locked false`, and
`--critical-fault true`. Use a fresh disposable environment for critical fault demos.
Ride start and completion send simulated unlock/lock commands after commit.
These commands model simulator state; they provide no hardware confirmation.

For a local SLO2 check, expose `health,metrics` via
`MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` on the backend **only in an isolated
test stack**. Query authenticated
`/actuator/metrics/fleet.telemetry.processing.lag` twice over a timed interval.
Divide the change in COUNT by elapsed seconds to check the rate, and divide the
change in TOTAL_TIME by the change in COUNT for mean lag. MAX is the rolling
maximum. The timer measures observation time to completed backend processing.
Use a fresh, disposable database for browser fixtures. The fixture override
disables scheduled expiry and MQTT and leaves the broker and simulator stopped.

```sh
docker compose -p kartylicious-fixtures -f docker-compose.yml -f docker-compose.fixtures.yml up -d --build --wait && (
  cd backend && \
  E2E_DB_URL=jdbc:postgresql://127.0.0.1:5432/kartylicious_e2e \
  E2E_DB_USER=kartylicious_e2e E2E_DB_PASSWORD=kartylicious_e2e_only \
  ./mvnw -B -Pe2e verify
)
```

The destructive reset hooks require explicit `E2E_DB_*` settings, a loopback
PostgreSQL URL and a database marker created when the fixture volume is first
initialized. Use a new Compose project name if you have an older fixture volume.
If the default ports are occupied, give the fixture project separate loopback
ports and set `E2E_BASE_URL` and `E2E_DB_URL` accordingly.

## Reservation expiry

The backend checks for ended active reservations every 30 seconds by default,
independently of MQTT. It expires each kart's due reservations and releases
their simulated deposit holds in a single transaction per kart. Configure the
interval with `RESERVATION_EXPIRY_INTERVAL` (for example, `10s`). The automated
test and browser fixture configurations disable scheduling for deterministic setup.

## Simulated payments

The gateway uses PostgreSQL to store simulated holds (`AUTHORIZED`, `RELEASED`
or `SETTLED`) and at most one fare capture per ride and hold. There are no card
details or money transfers. A reserved ride uses the booking ID as its hold
reference. An immediate ride uses its ride ID. Cancellation, expiry and
settlement participate in the same transactions as the booking or ride.

To demo a decline, set `SIMULATED_PAYMENT_DECLINE_USER_ID` to a synthetic
End User's UUID in your local Compose environment and recreate the backend
with `docker compose up -d --force-recreate backend`. This declines new holds
for that user while existing holds remain usable. Remove the setting and
recreate the backend to resume normal simulation.

To demo persistence, reserve a kart, start the ride during the reservation
window, then run `docker compose restart backend`. Log back in if needed,
report the simulated arrival using the command above, and end the ride.
For an isolated demo database, inspect the recorded payment with:

```sh
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select a.operation_id, a.status, s.ride_id, s.amount from payment_authorizations a left join payment_settlements s on s.authorization_id = a.operation_id"'
```

The gateway does not backfill holds from before persistent payments.
Legacy reservations without a hold can be cancelled or expired, and attempts
to start them fail authorization. A legacy active ride without a hold cannot
be settled. Use a fresh disposable database for demos involving legacy test
data, or handle the records explicitly before deployment.
