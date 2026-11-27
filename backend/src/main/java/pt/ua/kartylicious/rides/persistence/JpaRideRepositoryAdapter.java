package pt.ua.kartylicious.rides.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import pt.ua.kartylicious.rides.Ride;
import pt.ua.kartylicious.rides.RideStatus;
import pt.ua.kartylicious.rides.port.RideRepository;

@Repository
public class JpaRideRepositoryAdapter implements RideRepository {
  private final SpringDataRideRepository repository;

  public JpaRideRepositoryAdapter(SpringDataRideRepository repository) {
    this.repository = repository;
  }

  @Override
  public Ride save(Ride ride) {
    return repository.save(new JpaRideEntity(ride)).toRide();
  }

  @Override
  public List<Ride> findActiveByUser(UUID userId) {
    return repository.findByUserIdAndStatus(userId, RideStatus.ACTIVE).stream()
        .map(JpaRideEntity::toRide)
        .toList();
  }

  @Override
  public Optional<Ride> lockById(UUID rideId) {
    return repository.lockById(rideId).map(JpaRideEntity::toRide);
  }
}
