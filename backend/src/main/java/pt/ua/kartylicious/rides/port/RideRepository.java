package pt.ua.kartylicious.rides.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pt.ua.kartylicious.rides.Ride;

public interface RideRepository {
  Ride save(Ride ride);

  List<Ride> findActiveByUser(UUID userId);

  Optional<Ride> lockById(UUID rideId);
}
