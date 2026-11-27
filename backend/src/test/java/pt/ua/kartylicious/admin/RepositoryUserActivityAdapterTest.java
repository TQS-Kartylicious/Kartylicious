package pt.ua.kartylicious.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.kartylicious.admin.port.RepositoryUserActivityAdapter;
import pt.ua.kartylicious.bookings.Booking;
import pt.ua.kartylicious.bookings.BookingStatus;
import pt.ua.kartylicious.bookings.port.BookingRepository;
import pt.ua.kartylicious.rides.Ride;
import pt.ua.kartylicious.rides.RideStatus;
import pt.ua.kartylicious.rides.port.RideRepository;

@ExtendWith(MockitoExtension.class)
class RepositoryUserActivityAdapterTest {

  private static final Instant NOW = Instant.parse("2099-01-01T10:00:00Z");
  private static final UUID USER_ID = UUID.randomUUID();

  @Mock private BookingRepository bookingRepository;
  @Mock private RideRepository rideRepository;

  private RepositoryUserActivityAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter =
        new RepositoryUserActivityAdapter(
            bookingRepository, rideRepository, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  @Requirement("KARTY-76")
  void detectsCurrentOrFutureReservations() {
    when(bookingRepository.findActiveByUser(USER_ID))
        .thenReturn(
            List.of(
                new Booking(
                    UUID.randomUUID(),
                    USER_ID,
                    "hub-1",
                    "kart-1",
                    NOW.plusSeconds(60),
                    NOW.plusSeconds(600),
                    BookingStatus.ACTIVE)));

    assertThat(adapter.hasActiveRideOrReservation(USER_ID)).isTrue();
  }

  @Test
  @Requirement("KARTY-76")
  void ignoresEndedReservationsButDetectsActiveRides() {
    when(bookingRepository.findActiveByUser(USER_ID))
        .thenReturn(
            List.of(
                new Booking(
                    UUID.randomUUID(),
                    USER_ID,
                    "hub-1",
                    "kart-1",
                    NOW.minusSeconds(600),
                    NOW,
                    BookingStatus.ACTIVE)));
    when(rideRepository.findActiveByUser(USER_ID))
        .thenReturn(
            List.of(
                new Ride(
                    UUID.randomUUID(),
                    USER_ID,
                    "hub-1",
                    "kart-1",
                    NOW.minusSeconds(60),
                    RideStatus.ACTIVE)));

    assertThat(adapter.hasActiveRideOrReservation(USER_ID)).isTrue();
  }

  @Test
  @Requirement("KARTY-76")
  void reportsNoActivityWhenOnlyEndedReservationsExist() {
    when(bookingRepository.findActiveByUser(USER_ID))
        .thenReturn(
            List.of(
                new Booking(
                    UUID.randomUUID(),
                    USER_ID,
                    "hub-1",
                    "kart-1",
                    NOW.minusSeconds(600),
                    NOW,
                    BookingStatus.ACTIVE)));
    when(rideRepository.findActiveByUser(USER_ID)).thenReturn(List.of());

    assertThat(adapter.hasActiveRideOrReservation(USER_ID)).isFalse();
  }
}
