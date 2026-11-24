package pt.ua.kartylicious.rides;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.kartylicious.bookings.Booking;
import pt.ua.kartylicious.bookings.port.BookingRepository;
import pt.ua.kartylicious.hubs.Kart;
import pt.ua.kartylicious.hubs.KartStatus;
import pt.ua.kartylicious.hubs.port.HubRepository;
import pt.ua.kartylicious.rides.port.RideRepository;

@Service
public class RideService {
  private static final int MINIMUM_BATTERY_LEVEL = 20;

  private final HubRepository hubRepository;
  private final BookingRepository bookingRepository;
  private final RideRepository rideRepository;

  public RideService(
      HubRepository hubRepository,
      BookingRepository bookingRepository,
      RideRepository rideRepository) {
    this.hubRepository = hubRepository;
    this.bookingRepository = bookingRepository;
    this.rideRepository = rideRepository;
  }

  @Transactional
  public synchronized RideResponse start(RideRequest request, UUID userId) {
    if (request == null || request.hubId() == null || request.kartId() == null) {
      throw new RideValidationException("Hub and kart are required.");
    }
    if (!rideRepository.findActiveByUser(userId).isEmpty()) {
      throw new ActiveRideException();
    }

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
    if (kart.status() == KartStatus.RESERVED && !hasUserReservation(request, userId)) {
      throw new RideKartUnavailableException();
    }

    if (!hubRepository.startKart(request.hubId(), request.kartId())) {
      throw new RideKartUnavailableException();
    }
    var ride =
        new Ride(
            UUID.randomUUID(),
            userId,
            request.hubId(),
            request.kartId(),
            Instant.now(),
            RideStatus.ACTIVE);
    rideRepository.save(ride);
    return toResponse(ride, "Ride started.");
  }

  private Kart findKart(String hubId, String kartId) {
    return hubRepository
        .findById(hubId)
        .flatMap(hub -> hub.karts().stream().filter(kart -> kart.id().equals(kartId)).findFirst())
        .orElseThrow(RideKartUnavailableException::new);
  }

  private boolean hasUserReservation(RideRequest request, UUID userId) {
    return bookingRepository.findActiveByKart(request.hubId(), request.kartId()).stream()
        .map(Booking::userId)
        .anyMatch(userId::equals);
  }

  private boolean isEligibleStatus(KartStatus status) {
    return status == KartStatus.AVAILABLE || status == KartStatus.RESERVED;
  }

  private RideResponse toResponse(Ride ride, String message) {
    return new RideResponse(
        ride.id(), ride.hubId(), ride.kartId(), ride.startedAt(), ride.status().name(), message);
  }
}
