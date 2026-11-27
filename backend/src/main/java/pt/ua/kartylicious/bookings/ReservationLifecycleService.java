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
        .forEach(
            location -> {
              hubRepository.lockKartById(location.kartId()).orElseThrow(KartUnavailableException::new);
              expireEndedBookings(location.hubId(), location.kartId(), now);
            });
    return bookingRepository.findActiveByUser(userId);
  }

  public void complete(Booking booking) {
    bookingRepository.save(withStatus(booking, BookingStatus.COMPLETED));
  }

  @Transactional
  public void expireKart(String kartId) {
    hubRepository.lockKartById(kartId).orElseThrow(KartUnavailableException::new);
    var now = clock.instant();
    expireBookings(kartId, bookingRepository.findExpiredByKart(kartId, now), now);
  }

  /** The caller holds the kart lock. */
  public boolean hasUnexpiredReservationAtHub(String hubId, String kartId, Instant now) {
    return bookingRepository.findActiveByKart(hubId, kartId).stream()
        .anyMatch(booking -> booking.endsAt().isAfter(now));
  }

  /** The caller holds the kart lock. */
  public List<Booking> unexpiredAtOtherHubs(String hubId, String kartId, Instant now) {
    return bookingRepository.findUnexpiredAtOtherHubs(hubId, kartId, now);
  }

  @Transactional
  public Booking cancel(UUID bookingId, UUID userId) {
    var booking = findOwnedBooking(bookingId, userId);
    // Ride start and booking creation also serialize changes on the kart row.
    hubRepository.lockKartById(booking.kartId()).orElseThrow(KartUnavailableException::new);
    // Read a fresh snapshot after acquiring the kart lock.
    var current = findOwnedBooking(bookingId, userId);
    var now = clock.instant();
    if (current.status() != BookingStatus.ACTIVE || !current.endsAt().isAfter(now)) {
      throw new ReservationNotCancellableException();
    }

    var cancelled = bookingRepository.save(withStatus(current, BookingStatus.CANCELLED));
    hubRepository.reconcileReservedKart(
        current.kartId(),
        bookingRepository.hasOtherUnexpiredReservation(current.kartId(), bookingId, now));
    paymentPort.releaseSecurityDeposit(current.userId(), current.id());
    return cancelled;
  }

  private Booking findOwnedBooking(UUID bookingId, UUID userId) {
    var booking = bookingRepository.findById(bookingId).orElseThrow(BookingNotFoundException::new);
    if (!booking.userId().equals(userId)) {
      throw new BookingAccessDeniedException();
    }
    return booking;
  }

  private void expireEndedBookings(String hubId, String kartId, Instant now) {
    var endedBookings = bookingRepository.findActiveByKart(hubId, kartId).stream()
        .filter(booking -> !booking.endsAt().isAfter(now)).toList();
    expireBookings(kartId, endedBookings, now);
  }

  private void expireBookings(String kartId, List<Booking> endedBookings, Instant now) {
    endedBookings.forEach(
        booking -> {
          bookingRepository.save(withStatus(booking, BookingStatus.EXPIRED));
          paymentPort.releaseSecurityDeposit(booking.userId(), booking.id());
        });

    if (!endedBookings.isEmpty()) {
      hubRepository.reconcileReservedKart(
          kartId, bookingRepository.hasUnexpiredReservation(kartId, now));
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
