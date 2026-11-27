package pt.ua.kartylicious.bookings;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pt.ua.kartylicious.bookings.port.BookingRepository;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "reservation.expiry.enabled", havingValue = "true", matchIfMissing = true)
public class ReservationExpiryScheduler {
  private static final Logger LOGGER = LoggerFactory.getLogger(ReservationExpiryScheduler.class);

  private final BookingRepository bookings;
  private final ReservationLifecycleService lifecycle;
  private final Clock clock;

  public ReservationExpiryScheduler(
      BookingRepository bookings, ReservationLifecycleService lifecycle, Clock clock) {
    this.bookings = bookings;
    this.lifecycle = lifecycle;
    this.clock = clock;
  }

  @Scheduled(initialDelayString = "${reservation.expiry.interval:30s}",
      fixedDelayString = "${reservation.expiry.interval:30s}")
  public void expireDueReservations() {
    for (var kartId : bookings.findKartsWithExpiredReservations(clock.instant())) {
      try {
        lifecycle.expireKart(kartId);
      } catch (RuntimeException exception) {
        LOGGER.warn("Reservation expiry failed for kart {}", kartId, exception);
      }
    }
  }
}
