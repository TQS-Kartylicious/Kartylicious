package pt.ua.kartylicious.hubs;

import java.time.Instant;

public record TelemetrySnapshot(
    String kartId,
    String hubId,
    int batteryLevel,
    boolean criticalFault,
    Instant recordedAt) {}
