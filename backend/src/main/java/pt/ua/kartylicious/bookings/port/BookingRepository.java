package pt.ua.kartylicious.bookings.port;

import java.util.List;
import pt.ua.kartylicious.bookings.Booking;

public interface BookingRepository {
  Booking save(Booking booking);

  List<Booking> findActiveByKart(String hubId, String kartId);

  List<Booking> findActiveByUser(java.util.UUID userId);
}
