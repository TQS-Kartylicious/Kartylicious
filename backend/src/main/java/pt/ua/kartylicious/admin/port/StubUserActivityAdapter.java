package pt.ua.kartylicious.admin.port;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class StubUserActivityAdapter implements UserActivityPort {

  @Override
  public boolean hasActiveRideOrReservation(UUID userId) {
    // A real implementation will check rides and reservations in later stories.
    return false;
  }
}
