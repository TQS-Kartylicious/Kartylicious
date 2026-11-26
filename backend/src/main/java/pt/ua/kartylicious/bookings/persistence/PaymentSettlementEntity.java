package pt.ua.kartylicious.bookings.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_settlements")
class PaymentSettlementEntity {
  @Id private UUID rideId;
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "authorization_id", nullable = false, unique = true)
  private PaymentAuthorizationEntity authorization;
  @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
  @Column(nullable = false) private Instant settledAt;

  protected PaymentSettlementEntity() {}
}
