package pt.ua.kartylicious.rides.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import pt.ua.kartylicious.rides.RideStatus;

interface SpringDataRideRepository extends JpaRepository<JpaRideEntity, UUID> {
  List<JpaRideEntity> findByUserIdAndStatus(UUID userId, RideStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from JpaRideEntity r where r.id = :rideId")
  Optional<JpaRideEntity> lockById(UUID rideId);
}
