package pt.ua.kartylicious.bookings;

import java.time.Instant;
import java.util.UUID;

public record BookingResponse(
    UUID id,
    String hubId,
    String kartId,
    Instant startsAt,
    Instant endsAt,
    String status,
    String message) {}
