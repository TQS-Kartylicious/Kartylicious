package pt.ua.kartylicious.hubs.persistence;

import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.kartylicious.hubs.TelemetrySnapshot;
import pt.ua.kartylicious.hubs.VehicleLockCommand;
import pt.ua.kartylicious.hubs.port.TelemetryPort;

/** Reads accepted simulator observations and models transactional lock commands. */
@Component
public class SimulatedTelemetryAdapter implements TelemetryPort {
  private final SpringDataHubRepository repository;
  private final ApplicationEventPublisher events;

  public SimulatedTelemetryAdapter(SpringDataHubRepository repository, ApplicationEventPublisher events) {
    this.repository = repository;
    this.events = events;
  }

  @Override
  public Optional<TelemetrySnapshot> latestFor(String kartId) {
    return repository
        .findKartById(kartId)
        .filter(kart -> kart.getTelemetryRecordedAt() != null)
        .map(
            kart ->
                new TelemetrySnapshot(
                    kartId,
                    kart.getTelemetryHubId(),
                    kart.getBatteryLevel(),
                    kart.hasCriticalFault(),
                    kart.getTelemetryRecordedAt()));
  }

  @Override
  @Transactional
  public boolean lockVehicle(String kartId) {
    return repository
        .lockKartById(kartId)
        .map(
            kart -> {
              kart.setLocked(true);
              events.publishEvent(new VehicleLockCommand(kartId, true));
              return true;
            })
        .orElse(false);
  }

  @Override
  public void rideStarted(String kartId) {
    events.publishEvent(new VehicleLockCommand(kartId, false));
  }
}
