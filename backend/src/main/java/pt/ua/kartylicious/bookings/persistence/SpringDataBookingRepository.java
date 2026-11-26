package pt.ua.kartylicious.bookings.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.ua.kartylicious.bookings.Booking;
import pt.ua.kartylicious.bookings.BookingStatus;

interface SpringDataBookingRepository extends JpaRepository<JpaBookingEntity, UUID> {
  @Query("""
      select new pt.ua.kartylicious.bookings.Booking(
          b.id, b.userId, b.hubId, b.kartId, b.startsAt, b.endsAt, b.status)
      from JpaBookingEntity b where b.id = :bookingId
      """)
  Optional<Booking> findSnapshotById(@Param("bookingId") UUID bookingId);

  boolean existsByKartIdAndIdNotAndStatusAndEndsAtAfter(
      String kartId, UUID bookingId, BookingStatus status, Instant now);

  boolean existsByKartIdAndStatusAndEndsAtAfter(String kartId, BookingStatus status, Instant now);

  List<JpaBookingEntity> findByKartIdAndHubIdNotAndStatusAndEndsAtAfter(
      String kartId, String hubId, BookingStatus status, Instant now);

  List<JpaBookingEntity> findByHubIdAndKartIdAndStatus(
      String hubId, String kartId, BookingStatus status);

  List<JpaBookingEntity> findByUserIdAndStatus(UUID userId, BookingStatus status);

  @Query("""
      select distinct b.kartId from JpaBookingEntity b
      where b.status = :status and b.endsAt <= :now order by b.kartId
      """)
  List<String> findDueKartIds(@Param("status") BookingStatus status, @Param("now") Instant now);

  List<JpaBookingEntity> findByKartIdAndStatusAndEndsAtLessThanEqual(
      String kartId, BookingStatus status, Instant now);
}
