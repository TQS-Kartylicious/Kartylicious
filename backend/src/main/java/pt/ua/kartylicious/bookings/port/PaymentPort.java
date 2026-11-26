package pt.ua.kartylicious.bookings.port;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentPort {
  boolean authorizeSecurityDeposit(UUID userId, UUID operationId);

  boolean hasAuthorizedDeposit(UUID userId, UUID operationId);

  void releaseSecurityDeposit(UUID userId, UUID operationId);

  boolean captureFinalCharge(
      UUID userId, UUID authorizationId, UUID rideId, BigDecimal amount);
}
