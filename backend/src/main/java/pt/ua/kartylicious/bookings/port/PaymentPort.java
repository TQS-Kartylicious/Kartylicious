package pt.ua.kartylicious.bookings.port;

import java.util.UUID;

public interface PaymentPort {
  boolean authorizeSecurityDeposit(UUID userId, UUID operationId);

  void releaseSecurityDeposit(UUID userId, UUID operationId);
}
