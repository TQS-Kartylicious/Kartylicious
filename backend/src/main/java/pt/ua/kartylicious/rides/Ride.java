package pt.ua.kartylicious.rides;

import java.time.Instant;
import java.util.UUID;

public record Ride(
    UUID id, UUID userId, String hubId, String kartId, Instant startedAt, RideStatus status) {}
