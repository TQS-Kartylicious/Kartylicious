package pt.ua.kartylicious.bookings.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import pt.ua.kartylicious.bookings.Booking;
import pt.ua.kartylicious.bookings.BookingStatus;

@Entity
@Table(name = "bookings")
class JpaBookingEntity {
  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "hub_id", nullable = false, length = 64)
  private String hubId;

  @Column(name = "kart_id", nullable = false, length = 64)
  private String kartId;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  private Instant endsAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private BookingStatus status;

  protected JpaBookingEntity() {}

  JpaBookingEntity(Booking booking) {
    this.id = booking.id();
    this.userId = booking.userId();
    this.hubId = booking.hubId();
    this.kartId = booking.kartId();
    this.startsAt = booking.startsAt();
    this.endsAt = booking.endsAt();
    this.status = booking.status();
  }

  Booking toBooking() {
    return new Booking(id, userId, hubId, kartId, startsAt, endsAt, status);
  }
}
