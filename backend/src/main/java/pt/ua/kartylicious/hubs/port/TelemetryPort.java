package pt.ua.kartylicious.hubs.port;

import java.util.Optional;
import pt.ua.kartylicious.hubs.TelemetrySnapshot;

public interface TelemetryPort {
  Optional<TelemetrySnapshot> latestFor(String kartId);

  boolean lockVehicle(String kartId);

  void rideStarted(String kartId);
}
