package pt.ua.kartylicious.bookings;

import java.time.Instant;

public record BookingRequest(String hubId, String kartId, Instant startsAt, Instant endsAt) {}
