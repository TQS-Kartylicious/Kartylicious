package pt.ua.kartylicious.bookings.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_authorizations")
class PaymentAuthorizationEntity {
  @Id private UUID operationId;
  @Column(nullable = false) private UUID userId;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Status status;
  @Column(nullable = false) private Instant createdAt;
  @Column(nullable = false) private Instant updatedAt;

  protected PaymentAuthorizationEntity() {}

  enum Status {
    AUTHORIZED,
    RELEASED,
    SETTLED
  }
}
