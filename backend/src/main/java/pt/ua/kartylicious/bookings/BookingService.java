package pt.ua.kartylicious.bookings;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.kartylicious.bookings.port.BookingRepository;
import pt.ua.kartylicious.bookings.port.PaymentPort;
import pt.ua.kartylicious.hubs.Kart;
import pt.ua.kartylicious.hubs.KartStatus;
import pt.ua.kartylicious.hubs.port.HubRepository;

@Service
public class BookingService {
  public static final long DEFAULT_MAXIMUM_DURATION_MINUTES = 60;

  private final HubRepository hubRepository;
  private final BookingRepository bookingRepository;
  private final PaymentPort paymentPort;

  public BookingService(
      HubRepository hubRepository, BookingRepository bookingRepository, PaymentPort paymentPort) {
    this.hubRepository = hubRepository;
    this.bookingRepository = bookingRepository;
    this.paymentPort = paymentPort;
  }

  @Transactional
  public synchronized BookingResponse book(BookingRequest request, UUID userId) {
    validateDuration(request.startsAt(), request.endsAt());
    releaseExpiredReservation(request.hubId(), request.kartId());
    var kart = findKart(request.hubId(), request.kartId());
    if (!isBookable(kart, request.hubId(), request.kartId())) {
      throw new KartUnavailableException();
    }
    if (bookingRepository.findActiveByKart(request.hubId(), request.kartId()).stream()
        .anyMatch(existing -> overlaps(existing, request.startsAt(), request.endsAt()))) {
      throw new KartUnavailableException();
    }

    var bookingId = UUID.randomUUID();
    if (!paymentPort.authorizeSecurityDeposit(userId, bookingId)) {
      throw new DepositAuthorizationException();
    }
    if (!hubRepository.reserveKart(request.hubId(), request.kartId())) {
      throw new KartUnavailableException();
    }
    var booking =
        new Booking(
            bookingId,
            userId,
            request.hubId(),
            request.kartId(),
            request.startsAt(),
            request.endsAt(),
            BookingStatus.ACTIVE);
    bookingRepository.save(booking);
    return toResponse(booking, "Booking confirmed.");
  }

  private void releaseExpiredReservation(String hubId, String kartId) {
    var now = Instant.now();
    bookingRepository.findActiveByKart(hubId, kartId).stream()
        .filter(booking -> !booking.endsAt().isAfter(now))
        .findFirst()
        .ifPresent(booking -> hubRepository.releaseKart(hubId, kartId));
  }

  public List<BookingResponse> activeBookings(UUID userId) {
    return bookingRepository.findActiveByUser(userId).stream()
        .map(booking -> toResponse(booking, null))
        .toList();
  }

  private void validateDuration(Instant startsAt, Instant endsAt) {
    if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
      throw new BookingValidationException("Booking duration must be positive.");
    }
    if (!startsAt.isAfter(Instant.now())) {
      throw new BookingValidationException("Booking start time must be in the future.");
    }
    var durationSeconds = Duration.between(startsAt, endsAt).toSeconds();
    if (durationSeconds > DEFAULT_MAXIMUM_DURATION_MINUTES * 60) {
      throw new BookingValidationException(
          "Maximum permitted rental duration: " + DEFAULT_MAXIMUM_DURATION_MINUTES);
    }
  }

  private Kart findKart(String hubId, String kartId) {
    return hubRepository
        .findById(hubId)
        .flatMap(hub -> hub.karts().stream().filter(kart -> kart.id().equals(kartId)).findFirst())
        .orElseThrow(KartUnavailableException::new);
  }

  private boolean isBookable(Kart kart, String hubId, String kartId) {
    var statusAllowsBooking =
        kart.status() == KartStatus.AVAILABLE
            || (kart.status() == KartStatus.RESERVED
                && !bookingRepository.findActiveByKart(hubId, kartId).isEmpty());
    return statusAllowsBooking
        && kart.active()
        && kart.batteryLevel() >= 20
        && !kart.criticalFault();
  }

  private boolean overlaps(Booking existing, Instant startsAt, Instant endsAt) {
    return startsAt.isBefore(existing.endsAt()) && endsAt.isAfter(existing.startsAt());
  }

  private BookingResponse toResponse(Booking booking, String message) {
    return new BookingResponse(
        booking.id(),
        booking.hubId(),
        booking.kartId(),
        booking.startsAt(),
        booking.endsAt(),
        booking.status().name(),
        message);
  }
}
