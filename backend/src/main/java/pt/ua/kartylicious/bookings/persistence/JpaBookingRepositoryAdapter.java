package pt.ua.kartylicious.bookings.persistence;

import java.util.List;
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
}
