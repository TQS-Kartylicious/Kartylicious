package pt.ua.kartylicious.hubs;

public record Kart(
    String id,
    String name,
    int batteryLevel,
    KartStatus status,
    boolean active,
    boolean criticalFault) {}
