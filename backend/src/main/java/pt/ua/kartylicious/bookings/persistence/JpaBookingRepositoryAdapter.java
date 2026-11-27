package pt.ua.kartylicious.bookings.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import pt.ua.kartylicious.bookings.Booking;
import pt.ua.kartylicious.bookings.BookingStatus;
import pt.ua.kartylicious.bookings.port.BookingRepository;

@Repository
public class JpaBookingRepositoryAdapter implements BookingRepository {
  private final SpringDataBookingRepository repository;

  public JpaBookingRepositoryAdapter(SpringDataBookingRepository repository) {
    this.repository = repository;
  }

  @Override
  public Booking save(Booking booking) {
    return repository.save(new JpaBookingEntity(booking)).toBooking();
  }

  @Override
  public Optional<Booking> findById(UUID bookingId) {
    return repository.findSnapshotById(bookingId);
  }

  @Override
  public boolean hasOtherUnexpiredReservation(String kartId, UUID bookingId, Instant now) {
    return repository.existsByKartIdAndIdNotAndStatusAndEndsAtAfter(
        kartId, bookingId, BookingStatus.ACTIVE, now);
  }

  @Override
  public boolean hasUnexpiredReservation(String kartId, Instant now) {
    return repository.existsByKartIdAndStatusAndEndsAtAfter(kartId, BookingStatus.ACTIVE, now);
  }

  @Override
  public List<Booking> findUnexpiredAtOtherHubs(String hubId, String kartId, Instant now) {
    return repository.findByKartIdAndHubIdNotAndStatusAndEndsAtAfter(
            kartId, hubId, BookingStatus.ACTIVE, now).stream()
        .map(JpaBookingEntity::toBooking)
        .toList();
  }

  @Override
  public List<Booking> findActiveByKart(String hubId, String kartId) {
    return repository
        .findByHubIdAndKartIdAndStatus(hubId, kartId, BookingStatus.ACTIVE)
        .stream()
        .map(JpaBookingEntity::toBooking)
        .toList();
  }

  @Override
  public List<Booking> findActiveByUser(UUID userId) {
    return repository.findByUserIdAndStatus(userId, BookingStatus.ACTIVE).stream()
        .map(JpaBookingEntity::toBooking)
        .toList();
  }

  @Override
  public List<String> findKartsWithExpiredReservations(Instant now) {
    return repository.findDueKartIds(BookingStatus.ACTIVE, now);
  }

  @Override
  public List<Booking> findExpiredByKart(String kartId, Instant now) {
    return repository.findByKartIdAndStatusAndEndsAtLessThanEqual(kartId, BookingStatus.ACTIVE, now)
        .stream().map(JpaBookingEntity::toBooking).toList();
  }
}
