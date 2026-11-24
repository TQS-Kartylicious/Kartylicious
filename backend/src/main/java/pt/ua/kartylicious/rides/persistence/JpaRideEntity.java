package pt.ua.kartylicious.rides.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
    status = ride.status();
  }

  Ride toRide() {
    return new Ride(id, userId, hubId, kartId, startedAt, status);
  }
}
