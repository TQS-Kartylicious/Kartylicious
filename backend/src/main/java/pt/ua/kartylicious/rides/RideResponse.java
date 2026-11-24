package pt.ua.kartylicious.rides;

import java.time.Instant;
import java.util.UUID;

public record RideResponse(
    UUID id,
    String hubId,
    String kartId,
    Instant startedAt,
    String status,
    String message) {}
