package pt.ua.kartylicious.rides;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.kartylicious.bookings.Booking;
import pt.ua.kartylicious.bookings.ReservationLifecycleService;
import pt.ua.kartylicious.bookings.port.PaymentPort;
import pt.ua.kartylicious.hubs.Kart;
import pt.ua.kartylicious.hubs.KartStatus;
import pt.ua.kartylicious.hubs.port.HubRepository;
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
  private final Clock clock;

  public RideService(
      HubRepository hubRepository,
      ReservationLifecycleService reservationLifecycleService,
      RideRepository rideRepository,
      UserAccountRepository userAccountRepository,
      PaymentPort paymentPort,
      Clock clock) {
    this.hubRepository = hubRepository;
    this.reservationLifecycleService = reservationLifecycleService;
    this.rideRepository = rideRepository;
    this.userAccountRepository = userAccountRepository;
    this.paymentPort = paymentPort;
    this.clock = clock;
  }

  @Transactional
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
    if (kart.batteryLevel() < MINIMUM_BATTERY_LEVEL) {
      hubRepository.markKartLowBattery(request.hubId(), request.kartId());
      throw new LowBatteryException();
    }
    if (!isEligibleStatus(kart.status())) {
      throw new RideKartUnavailableException();
    }
    if (!kart.active() || kart.criticalFault()) {
      throw new RideKartUnavailableException();
    }

    var now = clock.instant();
    var validReservation = findValidReservation(activeBookings, userId, now);
    var expectedStatus = validReservation == null ? KartStatus.AVAILABLE : KartStatus.RESERVED;
    if (kart.status() != expectedStatus || (validReservation == null && !activeBookings.isEmpty())) {
      throw new RideKartUnavailableException();
    }

    if (!hubRepository.startKart(request.hubId(), request.kartId(), expectedStatus)) {
      throw new RideKartUnavailableException();
    }
    var rideId = UUID.randomUUID();
    var immediateRide = validReservation == null;
    if (immediateRide && !paymentPort.authorizeSecurityDeposit(userId, rideId)) {
      throw new RideDepositAuthorizationException();
    }
    var ride =
        new Ride(
            rideId,
            userId,
            request.hubId(),
            request.kartId(),
            now,
            RideStatus.ACTIVE);
    try {
      rideRepository.save(ride);
      if (validReservation != null) {
        reservationLifecycleService.complete(validReservation);
      }
    } catch (RuntimeException exception) {
      if (immediateRide) {
        paymentPort.releaseSecurityDeposit(userId, rideId);
      }
      throw exception;
    }
    return toResponse(ride, "Ride started.");
  }

  private Kart findKart(String hubId, String kartId) {
    return hubRepository
        .findById(hubId)
        .flatMap(hub -> hub.karts().stream().filter(kart -> kart.id().equals(kartId)).findFirst())
        .orElseThrow(RideKartUnavailableException::new);
  }

  private Booking findValidReservation(List<Booking> bookings, UUID userId, Instant now) {
    return bookings.stream()
        .filter(booking -> booking.userId().equals(userId))
        .filter(booking -> !booking.startsAt().isAfter(now))
        .filter(booking -> booking.endsAt().isAfter(now))
        .findFirst()
        .orElse(null);
  }

  private boolean isEligibleStatus(KartStatus status) {
    return status == KartStatus.AVAILABLE || status == KartStatus.RESERVED;
  }

  private RideResponse toResponse(Ride ride, String message) {
    return new RideResponse(
        ride.id(), ride.hubId(), ride.kartId(), ride.startedAt(), ride.status().name(), message);
  }
}
