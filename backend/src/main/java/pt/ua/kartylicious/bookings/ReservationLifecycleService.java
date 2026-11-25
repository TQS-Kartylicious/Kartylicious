package pt.ua.kartylicious.bookings;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.kartylicious.bookings.port.BookingRepository;
import pt.ua.kartylicious.bookings.port.PaymentPort;
import pt.ua.kartylicious.hubs.port.HubRepository;

@Service
public class ReservationLifecycleService {

  private final BookingRepository bookingRepository;
  private final HubRepository hubRepository;
  private final PaymentPort paymentPort;
  private final Clock clock;

  public ReservationLifecycleService(
      BookingRepository bookingRepository,
      HubRepository hubRepository,
      PaymentPort paymentPort,
      Clock clock) {
    this.bookingRepository = bookingRepository;
    this.hubRepository = hubRepository;
    this.paymentPort = paymentPort;
    this.clock = clock;
  }

  @Transactional
  public List<Booking> activeByKart(String hubId, String kartId) {
    hubRepository.lockKart(hubId, kartId).orElseThrow(KartUnavailableException::new);
    expireEndedBookings(hubId, kartId, clock.instant());
    return bookingRepository.findActiveByKart(hubId, kartId);
  }

  @Transactional
  public List<Booking> activeByUser(UUID userId) {
    var now = clock.instant();
    bookingRepository.findActiveByUser(userId).stream()
        .filter(booking -> !booking.endsAt().isAfter(now))
        .map(booking -> new KartLocation(booking.hubId(), booking.kartId()))
        .distinct()
        .forEach(location -> activeByKart(location.hubId(), location.kartId()));
    return bookingRepository.findActiveByUser(userId);
  }

  public void complete(Booking booking) {
    bookingRepository.save(withStatus(booking, BookingStatus.COMPLETED));
  }

  private void expireEndedBookings(String hubId, String kartId, Instant now) {
    var activeBookings = bookingRepository.findActiveByKart(hubId, kartId);
    var endedBookings =
        activeBookings.stream().filter(booking -> !booking.endsAt().isAfter(now)).toList();

    endedBookings.forEach(
        booking -> {
          bookingRepository.save(withStatus(booking, BookingStatus.EXPIRED));
          paymentPort.releaseSecurityDeposit(booking.userId(), booking.id());
        });

    if (!endedBookings.isEmpty()
        && activeBookings.stream().allMatch(booking -> !booking.endsAt().isAfter(now))) {
      hubRepository.releaseKart(hubId, kartId);
    }
  }

  private Booking withStatus(Booking booking, BookingStatus status) {
    return new Booking(
        booking.id(),
        booking.userId(),
        booking.hubId(),
        booking.kartId(),
        booking.startsAt(),
        booking.endsAt(),
        status);
  }

  private record KartLocation(String hubId, String kartId) {}
}
