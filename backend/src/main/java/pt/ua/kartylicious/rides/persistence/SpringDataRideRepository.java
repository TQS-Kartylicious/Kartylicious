package pt.ua.kartylicious.rides.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.ua.kartylicious.rides.RideStatus;

interface SpringDataRideRepository extends JpaRepository<JpaRideEntity, UUID> {
  List<JpaRideEntity> findByUserIdAndStatus(UUID userId, RideStatus status);
}
