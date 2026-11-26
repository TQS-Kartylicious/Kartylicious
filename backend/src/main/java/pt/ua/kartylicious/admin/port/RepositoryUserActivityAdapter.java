package pt.ua.kartylicious.admin.port;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pt.ua.kartylicious.bookings.port.BookingRepository;
import pt.ua.kartylicious.rides.port.RideRepository;

@Component
public class RepositoryUserActivityAdapter implements UserActivityPort {

  private final BookingRepository bookingRepository;
  private final RideRepository rideRepository;
  private final Clock clock;

  public RepositoryUserActivityAdapter(
      BookingRepository bookingRepository, RideRepository rideRepository, Clock clock) {
    this.bookingRepository = bookingRepository;
    this.rideRepository = rideRepository;
    this.clock = clock;
  }

  @Override
  public boolean hasActiveRideOrReservation(UUID userId) {
    var now = clock.instant();
    var hasActiveReservation =
        bookingRepository.findActiveByUser(userId).stream()
            .anyMatch(booking -> booking.endsAt().isAfter(now));
    return hasActiveReservation || !rideRepository.findActiveByUser(userId).isEmpty();
  }
}
