package pt.ua.kartylicious.bookings;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.kartylicious.bookings.port.BookingRepository;
import pt.ua.kartylicious.bookings.port.PaymentPort;
import pt.ua.kartylicious.config.MunicipalRulesProperties;
import pt.ua.kartylicious.hubs.Kart;
import pt.ua.kartylicious.hubs.KartStatus;
import pt.ua.kartylicious.hubs.port.HubRepository;
import pt.ua.kartylicious.registration.port.UserAccountRepository;

@Service
public class BookingService {
  private final HubRepository hubRepository;
  private final BookingRepository bookingRepository;
  private final PaymentPort paymentPort;
  private final UserAccountRepository userAccountRepository;
  private final ReservationLifecycleService reservationLifecycleService;
  private final MunicipalRulesProperties municipalRules;
  private final Clock clock;

  public BookingService(
      HubRepository hubRepository,
      BookingRepository bookingRepository,
      PaymentPort paymentPort,
      UserAccountRepository userAccountRepository,
      ReservationLifecycleService reservationLifecycleService,
      MunicipalRulesProperties municipalRules,
      Clock clock) {
    this.hubRepository = hubRepository;
    this.bookingRepository = bookingRepository;
    this.paymentPort = paymentPort;
    this.userAccountRepository = userAccountRepository;
    this.reservationLifecycleService = reservationLifecycleService;
    this.municipalRules = municipalRules;
    this.clock = clock;
  }

  @Transactional
  public BookingResponse book(BookingRequest request, UUID userId) {
    validateDuration(request.startsAt(), request.endsAt());
    var user =
        userAccountRepository
            .lockById(userId)
            .orElseThrow(() -> new BookingValidationException("User account not found."));
    if (!user.active()) {
      throw new BookingValidationException("User account is inactive.");
    }
    var activeBookings =
        reservationLifecycleService.activeByKart(request.hubId(), request.kartId());
    var kart = findKart(request.hubId(), request.kartId());
    if (!isBookable(kart, activeBookings)) {
      throw new KartUnavailableException();
    }
    if (activeBookings.stream()
        .anyMatch(existing -> overlaps(existing, request.startsAt(), request.endsAt()))) {
      throw new KartUnavailableException();
    }
    if (!hubRepository.reserveKart(request.hubId(), request.kartId())) {
      throw new KartUnavailableException();
    }

    var bookingId = UUID.randomUUID();
    if (!paymentPort.authorizeSecurityDeposit(userId, bookingId)) {
      throw new DepositAuthorizationException();
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
    try {
      bookingRepository.save(booking);
    } catch (RuntimeException exception) {
      paymentPort.releaseSecurityDeposit(userId, bookingId);
      throw exception;
    }
    return toResponse(booking, "Booking confirmed.");
  }

  @Transactional
  public List<BookingResponse> activeBookings(UUID userId) {
    return reservationLifecycleService.activeByUser(userId).stream()
        .map(booking -> toResponse(booking, null))
        .toList();
  }

  private void validateDuration(Instant startsAt, Instant endsAt) {
    if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
      throw new BookingValidationException("Booking duration must be positive.");
    }
    if (!startsAt.isAfter(clock.instant())) {
      throw new BookingValidationException("Booking start time must be in the future.");
    }
    var maximumDurationMinutes = municipalRules.getMaximumReservationDurationMinutes();
    var duration = Duration.between(startsAt, endsAt);
    if (duration.compareTo(Duration.ofMinutes(maximumDurationMinutes)) > 0) {
      throw new BookingValidationException(
          "Maximum permitted rental duration: " + maximumDurationMinutes);
    }
  }

  private Kart findKart(String hubId, String kartId) {
    return hubRepository
        .findById(hubId)
        .flatMap(hub -> hub.karts().stream().filter(kart -> kart.id().equals(kartId)).findFirst())
        .orElseThrow(KartUnavailableException::new);
  }

  private boolean isBookable(Kart kart, List<Booking> activeBookings) {
    var statusAllowsBooking =
        kart.status() == KartStatus.AVAILABLE
            || (kart.status() == KartStatus.RESERVED && !activeBookings.isEmpty());
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
