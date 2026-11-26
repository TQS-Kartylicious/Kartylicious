package pt.ua.kartylicious.bookings.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pt.ua.kartylicious.bookings.Booking;

public interface BookingRepository {
  Booking save(Booking booking);

  Optional<Booking> findById(UUID bookingId);

  boolean hasOtherUnexpiredReservation(String kartId, UUID bookingId, Instant now);

  boolean hasUnexpiredReservation(String kartId, Instant now);

  List<Booking> findUnexpiredAtOtherHubs(String hubId, String kartId, Instant now);

  List<Booking> findActiveByKart(String hubId, String kartId);

  List<Booking> findActiveByUser(UUID userId);

  List<String> findKartsWithExpiredReservations(Instant now);

  List<Booking> findExpiredByKart(String kartId, Instant now);
}
