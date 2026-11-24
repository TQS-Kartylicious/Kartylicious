package pt.ua.kartylicious.bookings.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.ua.kartylicious.bookings.BookingStatus;

interface SpringDataBookingRepository extends JpaRepository<JpaBookingEntity, UUID> {
  List<JpaBookingEntity> findByHubIdAndKartIdAndStatus(
      String hubId, String kartId, BookingStatus status);

  List<JpaBookingEntity> findByUserIdAndStatus(UUID userId, BookingStatus status);
}
