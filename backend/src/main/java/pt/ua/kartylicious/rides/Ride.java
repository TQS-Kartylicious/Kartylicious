package pt.ua.kartylicious.rides;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Ride(
    UUID id,
    UUID userId,
    String hubId,
    String kartId,
    Instant startedAt,
    Instant endedAt,
    String endHubId,
    BigDecimal finalCharge,
    UUID paymentAuthorizationId,
    RideStatus status) {

  public Ride(
      UUID id,
      UUID userId,
      String hubId,
      String kartId,
      Instant startedAt,
      RideStatus status) {
    this(id, userId, hubId, kartId, startedAt, null, null, null, id, status);
  }
}
