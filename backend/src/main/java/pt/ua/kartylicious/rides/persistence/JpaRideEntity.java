package pt.ua.kartylicious.rides.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import pt.ua.kartylicious.rides.Ride;
import pt.ua.kartylicious.rides.RideStatus;

@Entity
@Table(name = "rides")
class JpaRideEntity {
  @Id private UUID id;
  @Column(name = "user_id", nullable = false) private UUID userId;
  @Column(name = "hub_id", nullable = false, length = 64) private String hubId;
  @Column(name = "kart_id", nullable = false, length = 64) private String kartId;
  @Column(name = "started_at", nullable = false) private Instant startedAt;
  @Column(name = "ended_at") private Instant endedAt;
  @Column(name = "end_hub_id", length = 64) private String endHubId;
  @Column(name = "final_charge", precision = 12, scale = 2) private BigDecimal finalCharge;
  @Column(name = "payment_authorization_id") private UUID paymentAuthorizationId;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private RideStatus status;

  protected JpaRideEntity() {}

  JpaRideEntity(Ride ride) {
    id = ride.id();
    userId = ride.userId();
    hubId = ride.hubId();
    kartId = ride.kartId();
    startedAt = ride.startedAt();
    endedAt = ride.endedAt();
    endHubId = ride.endHubId();
    finalCharge = ride.finalCharge();
    paymentAuthorizationId = ride.paymentAuthorizationId();
    status = ride.status();
  }

  Ride toRide() {
    return new Ride(
        id,
        userId,
        hubId,
        kartId,
        startedAt,
        endedAt,
        endHubId,
        finalCharge,
        paymentAuthorizationId,
        status);
  }
}
