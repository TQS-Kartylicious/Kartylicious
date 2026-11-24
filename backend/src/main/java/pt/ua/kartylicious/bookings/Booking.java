package pt.ua.kartylicious.bookings;

import java.time.Instant;
import java.util.UUID;

public record Booking(
    UUID id,
    UUID userId,
    String hubId,
    String kartId,
    Instant startsAt,
    Instant endsAt,
    BookingStatus status) {}
