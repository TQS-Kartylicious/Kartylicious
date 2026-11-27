package pt.ua.kartylicious.rides;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.kartylicious.bookings.Booking;
import pt.ua.kartylicious.bookings.ReservationLifecycleService;
import pt.ua.kartylicious.bookings.port.PaymentPort;
import pt.ua.kartylicious.config.MunicipalRulesProperties;
import pt.ua.kartylicious.hubs.Kart;
import pt.ua.kartylicious.hubs.KartStatus;
import pt.ua.kartylicious.hubs.port.HubRepository;
import pt.ua.kartylicious.hubs.port.TelemetryPort;
import pt.ua.kartylicious.registration.port.UserAccountRepository;
import pt.ua.kartylicious.rides.port.RideRepository;

@Service
public class RideService {
  private static final int MINIMUM_BATTERY_LEVEL = 20;

  private final HubRepository hubRepository;
  private final ReservationLifecycleService reservationLifecycleService;
  private final RideRepository rideRepository;
  private final UserAccountRepository userAccountRepository;
  private final PaymentPort paymentPort;
  private final TelemetryPort telemetryPort;
  private final MunicipalRulesProperties municipalRules;
  private final Clock clock;

  public RideService(
      HubRepository hubRepository,
      ReservationLifecycleService reservationLifecycleService,
      RideRepository rideRepository,
      UserAccountRepository userAccountRepository,
      PaymentPort paymentPort,
      TelemetryPort telemetryPort,
      MunicipalRulesProperties municipalRules,
      Clock clock) {
    this.hubRepository = hubRepository;
    this.reservationLifecycleService = reservationLifecycleService;
    this.rideRepository = rideRepository;
    this.userAccountRepository = userAccountRepository;
    this.paymentPort = paymentPort;
    this.telemetryPort = telemetryPort;
    this.municipalRules = municipalRules;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Optional<RideResponse> activeRide(UUID userId) {
    return rideRepository.findActiveByUser(userId).stream()
        .findFirst()
        .map(ride -> toResponse(ride, null));
  }

  // This rejection happens before starting a kart or authorizing a deposit.
  @Transactional(noRollbackFor = LowBatteryException.class)
  public RideResponse start(RideRequest request, UUID userId) {
    if (request == null || request.hubId() == null || request.kartId() == null) {
      throw new RideValidationException("Hub and kart are required.");
    }
    var user =
        userAccountRepository
            .lockById(userId)
            .orElseThrow(() -> new RideValidationException("User account not found."));
    if (!user.active()) {
      throw new RideValidationException("User account is inactive.");
    }
    if (!rideRepository.findActiveByUser(userId).isEmpty()) {
      throw new ActiveRideException();
    }

    var activeBookings =
        reservationLifecycleService.activeByKart(request.hubId(), request.kartId());
    var kart = findKart(request.hubId(), request.kartId());
    if (!kart.active() || kart.criticalFault()) {
      throw new RideKartUnavailableException();
    }
    if (kart.batteryLevel() < MINIMUM_BATTERY_LEVEL
        && (isEligibleStatus(kart.status()) || kart.status() == KartStatus.LOW_BATTERY)) {
      if (isEligibleStatus(kart.status())) {
        hubRepository.markKartLowBattery(request.hubId(), request.kartId());
      }
      throw new LowBatteryException();
    }
    if (!isEligibleStatus(kart.status())) {
      throw new RideKartUnavailableException();
    }

    var now = clock.instant();
    var validReservation = findValidReservation(activeBookings, userId, request.bookingId(), now);
    if (request.bookingId() != null && validReservation == null) {
      throw new RideKartUnavailableException();
    }
    var expectedStatus = validReservation == null ? KartStatus.AVAILABLE : KartStatus.RESERVED;
    if (kart.status() != expectedStatus
        || (validReservation == null
            && (!activeBookings.isEmpty()
                || !reservationLifecycleService.unexpiredAtOtherHubs(
                        request.hubId(), request.kartId(), now).isEmpty()))) {
      throw new RideKartUnavailableException();
    }

    if (!hubRepository.startKart(request.hubId(), request.kartId(), expectedStatus)) {
      throw new RideKartUnavailableException();
    }
    var rideId = UUID.randomUUID();
    var authorizationId = validReservation == null ? rideId : validReservation.id();
    var authorized = validReservation == null
        ? paymentPort.authorizeSecurityDeposit(userId, authorizationId)
        : paymentPort.hasAuthorizedDeposit(userId, authorizationId);
    if (!authorized) {
      throw new RideDepositAuthorizationException();
    }
    var ride =
        new Ride(
            rideId,
            userId,
            request.hubId(),
            request.kartId(),
            now,
            null,
            null,
            null,
            authorizationId,
            RideStatus.ACTIVE);
    rideRepository.save(ride);
    if (validReservation != null) {
      reservationLifecycleService.complete(validReservation);
    }
    telemetryPort.rideStarted(ride.kartId());
    return toResponse(ride, "Ride started.");
  }

  @Transactional
  public RideCompletionResponse end(
      UUID rideId, RideCompletionRequest request, UUID userId) {
    if (request == null || request.hubId() == null || request.hubId().isBlank()) {
      throw new RideValidationException("Destination hub is required.");
    }
    var ride =
        rideRepository.lockById(rideId).orElseThrow(RideNotFoundException::new);
    if (!ride.userId().equals(userId)) {
      throw new RideAccessDeniedException();
    }
    if (ride.status() != RideStatus.ACTIVE) {
      throw new RideAlreadyCompletedException();
    }

    var kart =
        hubRepository.lockKartById(ride.kartId()).orElseThrow(RideCompletionException::new);
    var destinationHub =
        hubRepository.lockHub(request.hubId()).orElseThrow(UnauthorizedHubException::new);
    var telemetry =
        telemetryPort.latestFor(ride.kartId()).orElseThrow(UnauthorizedHubException::new);
    if (!request.hubId().equals(telemetry.hubId())
        || telemetry.recordedAt() == null
        || telemetry.recordedAt().isBefore(ride.startedAt())
        || telemetry.recordedAt().isAfter(clock.instant())) {
      throw new UnauthorizedHubException();
    }

    if (destinationHub.occupancy() >= destinationHub.capacity()) {
      throw new HubFullException();
    }

    var endedAt = clock.instant();
    if (endedAt.isBefore(ride.startedAt())) {
      throw new RideValidationException("Ride end time cannot precede its start time.");
    }
    var finalCharge = calculateFinalCharge(ride.startedAt(), endedAt);
    var returnedStatus =
        returnedStatus(
            kart.active(), telemetry.batteryLevel(), kart.criticalFault() || telemetry.criticalFault());
    if (returnedStatus == KartStatus.AVAILABLE
        && ride.hubId().equals(destinationHub.id())
        && reservationLifecycleService.hasUnexpiredReservationAtHub(
            destinationHub.id(), ride.kartId(), endedAt)) {
      returnedStatus = KartStatus.RESERVED;
    }

    if (ride.paymentAuthorizationId() == null) {
      throw new FinalPaymentException();
    }
    if (!telemetryPort.lockVehicle(ride.kartId())) {
      throw new VehicleLockException();
    }
    if (!paymentPort.captureFinalCharge(
        userId, ride.paymentAuthorizationId(), ride.id(), finalCharge)) {
      throw new FinalPaymentException();
    }
    if (!hubRepository.parkKartAtHub(request.hubId(), ride.kartId(), returnedStatus)) {
      throw new RideCompletionException();
    }

    var completedRide =
        new Ride(
            ride.id(),
            ride.userId(),
            ride.hubId(),
            ride.kartId(),
            ride.startedAt(),
            endedAt,
            request.hubId(),
            finalCharge,
            ride.paymentAuthorizationId(),
            RideStatus.COMPLETED);
    rideRepository.save(completedRide);
    return new RideCompletionResponse(
        ride.id(),
        request.hubId(),
        endedAt,
        RideStatus.COMPLETED.name(),
        returnedStatus.name(),
        finalCharge,
        "Ride completed.");
  }

  private Kart findKart(String hubId, String kartId) {
    return hubRepository
        .findById(hubId)
        .flatMap(hub -> hub.karts().stream().filter(kart -> kart.id().equals(kartId)).findFirst())
        .orElseThrow(RideKartUnavailableException::new);
  }

  private Booking findValidReservation(
      List<Booking> bookings, UUID userId, UUID bookingId, Instant now) {
    return bookings.stream()
        .filter(booking -> bookingId == null || booking.id().equals(bookingId))
        .filter(booking -> booking.userId().equals(userId))
        .filter(booking -> !booking.startsAt().isAfter(now))
        .filter(booking -> booking.endsAt().isAfter(now))
        .findFirst()
        .orElse(null);
  }

  private boolean isEligibleStatus(KartStatus status) {
    return status == KartStatus.AVAILABLE || status == KartStatus.RESERVED;
  }

  private BigDecimal calculateFinalCharge(Instant startedAt, Instant endedAt) {
    var duration = Duration.between(startedAt, endedAt);
    var elapsedMinutes =
        BigDecimal.valueOf(duration.toMillis())
            .divide(BigDecimal.valueOf(60_000), 8, RoundingMode.HALF_UP);
    var charge =
        municipalRules
            .getBaseFare()
            .add(municipalRules.getPerMinuteRate().multiply(elapsedMinutes));
    if (duration.compareTo(
            Duration.ofMinutes(municipalRules.getMaximumRideDurationMinutes()))
        > 0) {
      charge = charge.add(municipalRules.getOvertimeFee());
    }
    return charge.setScale(2, RoundingMode.HALF_UP);
  }

  private KartStatus returnedStatus(
      boolean active, int batteryLevel, boolean criticalFault) {
    if (!active) {
      return KartStatus.INACTIVE;
    }
    if (criticalFault) {
      return KartStatus.IN_MAINTENANCE;
    }
    return batteryLevel < MINIMUM_BATTERY_LEVEL
        ? KartStatus.LOW_BATTERY
        : KartStatus.AVAILABLE;
  }

  private RideResponse toResponse(Ride ride, String message) {
    return new RideResponse(
        ride.id(), ride.hubId(), ride.kartId(), ride.startedAt(), ride.status().name(), message);
  }
}
