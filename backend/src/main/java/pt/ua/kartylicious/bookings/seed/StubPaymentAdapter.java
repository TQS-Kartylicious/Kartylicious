package pt.ua.kartylicious.bookings.seed;

import java.util.UUID;
import org.springframework.stereotype.Component;
import pt.ua.kartylicious.bookings.port.PaymentPort;

/** Payment integration seam; authorization succeeds until a real gateway is introduced. */
@Component
public class StubPaymentAdapter implements PaymentPort {
  @Override
  public boolean authorizeSecurityDeposit(UUID userId, UUID bookingId) {
    return true;
  }
}
