BEGIN;

INSERT INTO hubs (id, name, address, capacity)
SELECT 'perf-hub-' || n, 'Synthetic performance hub ' || n, 'Synthetic test address', 12
FROM generate_series(1, 5) AS n;

INSERT INTO hubs (id, name, address, capacity)
VALUES ('perf-return', 'Synthetic return hub', 'Synthetic test address', 2);

INSERT INTO karts (id, name, hub_id, battery_level, status, active, critical_fault, locked)
SELECT 'perf-kart-' || n, 'Synthetic performance kart ' || n,
       'perf-hub-' || ((n - 1) / 10 + 1), 80, 'AVAILABLE', true, false, true
FROM generate_series(1, 50) AS n;

INSERT INTO karts (id, name, hub_id, battery_level, status, active, critical_fault, locked)
VALUES ('perf-conflict', 'Conflict fixture kart', 'perf-hub-5', 80, 'AVAILABLE', true, false, true),
       ('perf-race', 'Contended kart', 'perf-hub-5', 80, 'AVAILABLE', true, false, true),
       ('perf-parked', 'Parked fixture kart', 'perf-return', 80, 'AVAILABLE', true, false, true);

COMMIT;
