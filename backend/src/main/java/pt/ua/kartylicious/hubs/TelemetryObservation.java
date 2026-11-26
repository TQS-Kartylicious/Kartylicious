package pt.ua.kartylicious.hubs;

import java.time.Instant;

public record TelemetryObservation(
    String kartId, String hubId, int batteryLevel, boolean locked, boolean criticalFault,
    Instant recordedAt) {}
