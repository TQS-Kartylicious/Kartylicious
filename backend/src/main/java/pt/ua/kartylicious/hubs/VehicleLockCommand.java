package pt.ua.kartylicious.hubs;

public record VehicleLockCommand(String kartId, boolean locked) {}
