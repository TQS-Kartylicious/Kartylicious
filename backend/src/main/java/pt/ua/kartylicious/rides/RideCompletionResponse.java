package pt.ua.kartylicious.rides;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RideCompletionResponse(
    UUID id,
    String endHubId,
    Instant endedAt,
    String status,
    String kartStatus,
    BigDecimal finalCharge,
    String message) {}
