package pt.ua.kartylicious.bookings;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
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
    var startsAt = atStoragePrecision(request.startsAt());
    var endsAt = atStoragePrecision(request.endsAt());
    if (!endsAt.isAfter(startsAt)) {
      throw new BookingValidationException("Booking duration must be positive.");
    }
    var user =
        userAccountRepository
            .lockById(userId)
            .orElseThrow(() -> new BookingValidationException("User account not found."));
    if (!user.active()) {
      throw new BookingValidationException("User account is inactive.");
    }
    var activeBookings =
        reservationLifecycleService.activeByKart(request.hubId(), request.kartId());
    var elsewhere = reservationLifecycleService.unexpiredAtOtherHubs(
        request.hubId(), request.kartId(), clock.instant());
    var kart = findKart(request.hubId(), request.kartId());
    if (!isBookable(kart, !activeBookings.isEmpty() || !elsewhere.isEmpty())) {
      throw new KartUnavailableException();
    }
    if (Stream.concat(activeBookings.stream(), elsewhere.stream())
        .anyMatch(existing -> overlaps(existing, startsAt, endsAt))) {
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
            startsAt,
            endsAt,
            BookingStatus.ACTIVE);
    bookingRepository.save(booking);
    return toResponse(booking, "Booking confirmed.");
  }

  @Transactional
  public List<BookingResponse> activeBookings(UUID userId) {
    return reservationLifecycleService.activeByUser(userId).stream()
        .map(booking -> toResponse(booking, null))
        .toList();
  }

  public BookingResponse cancel(UUID bookingId, UUID userId) {
    return toResponse(reservationLifecycleService.cancel(bookingId, userId), "Reservation cancelled.");
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

  private boolean isBookable(Kart kart, boolean hasReservations) {
    var statusAllowsBooking =
        kart.status() == KartStatus.AVAILABLE
            || (kart.status() == KartStatus.RESERVED && hasReservations);
    return statusAllowsBooking
        && kart.active()
        && kart.batteryLevel() >= 20
        && !kart.criticalFault();
  }

  private boolean overlaps(Booking existing, Instant startsAt, Instant endsAt) {
    return startsAt.isBefore(existing.endsAt()) && endsAt.isAfter(existing.startsAt());
  }

  private Instant atStoragePrecision(Instant instant) {
    // PostgreSQL rounds timestamps to microseconds. Compare and persist the same boundary.
    return instant.plusNanos(500).truncatedTo(ChronoUnit.MICROS);
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
