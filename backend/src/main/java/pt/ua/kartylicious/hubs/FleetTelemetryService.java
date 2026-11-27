package pt.ua.kartylicious.hubs;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.kartylicious.bookings.port.BookingRepository;
import pt.ua.kartylicious.faults.FaultService;
import pt.ua.kartylicious.hubs.port.HubRepository;

@Service
public class FleetTelemetryService {
  private final HubRepository hubs;
  private final BookingRepository bookings;
  private final FaultService faults;
  private final Clock clock;

  public FleetTelemetryService(
      HubRepository hubs, BookingRepository bookings, FaultService faults, Clock clock) {
    this.hubs = hubs;
    this.bookings = bookings;
    this.faults = faults;
    this.clock = clock;
  }

  @Transactional
  public void ingest(TelemetryObservation reading) {
    var now = clock.instant();
    if (reading.recordedAt() == null || reading.recordedAt().isAfter(now)
        || reading.batteryLevel() < 0 || reading.batteryLevel() > 100) {
      throw new IllegalArgumentException("Invalid telemetry timestamp or battery level");
    }
    if (reading.hubId() != null && !hubs.hubExists(reading.hubId())) {
      throw new IllegalArgumentException("Unknown observed hub");
    }
    var kart = hubs.lockKartById(reading.kartId())
        .orElseThrow(() -> new IllegalArgumentException("Unknown observed kart"));
    boolean hasReservation = kart.status() == KartStatus.LOW_BATTERY
        && reading.batteryLevel() >= 20
        && bookings.hasUnexpiredReservation(kart.id(), now);
    var normalized = new TelemetryObservation(
        reading.kartId(), reading.hubId(), reading.batteryLevel(), reading.locked(),
        reading.criticalFault(), reading.recordedAt().truncatedTo(ChronoUnit.MICROS));
    if (hubs.recordTelemetry(normalized, hasReservation)) {
      faults.reportCriticalTelemetry(reading.kartId(), normalized.recordedAt());
    }
  }
}
