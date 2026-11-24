package pt.ua.kartylicious.rides;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.kartylicious.bookings.Booking;
import pt.ua.kartylicious.bookings.BookingStatus;
import pt.ua.kartylicious.bookings.port.BookingRepository;
import pt.ua.kartylicious.hubs.Hub;
import pt.ua.kartylicious.hubs.Kart;
import pt.ua.kartylicious.hubs.KartStatus;
import pt.ua.kartylicious.hubs.port.HubRepository;
import pt.ua.kartylicious.rides.port.RideRepository;

@ExtendWith(MockitoExtension.class)
@Requirement("US3.3")
class RideServiceTest {
  private static final String HUB_ID = "hub-1";
  private static final String KART_ID = "kart-1";
  private static final UUID USER_ID = UUID.randomUUID();

  @Mock private HubRepository hubRepository;
  @Mock private BookingRepository bookingRepository;
  @Mock private RideRepository rideRepository;

  private RideService rideService;

  @BeforeEach
  void setUp() {
    rideService = new RideService(hubRepository, bookingRepository, rideRepository);
    when(rideRepository.findActiveByUser(USER_ID)).thenReturn(List.of());
    lenient().when(hubRepository.startKart(HUB_ID, KART_ID)).thenReturn(true);
  }

  @Test
  void startsAnAvailableRideAndStartsTheTripTimer() {
    givenKart(new Kart(KART_ID, "Kart 1", 80, KartStatus.AVAILABLE, true, false));

    var response = rideService.start(new RideRequest(HUB_ID, KART_ID), USER_ID);

    assertThat(response.status()).isEqualTo(RideStatus.ACTIVE.name());
    assertThat(response.message()).isEqualTo("Ride started.");
    assertThat(response.startedAt()).isNotNull();
    verify(hubRepository).startKart(HUB_ID, KART_ID);
    verify(rideRepository).save(any(Ride.class));
  }

  @Test
  void startsAReservedRideOnlyForTheUserWhoReservedIt() {
    givenKart(new Kart(KART_ID, "Kart 1", 80, KartStatus.RESERVED, true, false));
    when(bookingRepository.findActiveByKart(HUB_ID, KART_ID))
        .thenReturn(List.of(activeBooking(USER_ID)));

    rideService.start(new RideRequest(HUB_ID, KART_ID), USER_ID);

    verify(hubRepository).startKart(HUB_ID, KART_ID);
  }

  @Test
  void rejectsAReservedRideOwnedByAnotherUser() {
    givenKart(new Kart(KART_ID, "Kart 1", 80, KartStatus.RESERVED, true, false));
    when(bookingRepository.findActiveByKart(HUB_ID, KART_ID))
        .thenReturn(List.of(activeBooking(UUID.randomUUID())));

    assertThatThrownBy(() -> rideService.start(new RideRequest(HUB_ID, KART_ID), USER_ID))
        .isInstanceOf(RideKartUnavailableException.class);
    verify(hubRepository, never()).startKart(HUB_ID, KART_ID);
  }

  @Test
  void rejectsNineteenPercentAndFlagsTheKartAsLowBattery() {
    givenKart(new Kart(KART_ID, "Kart 1", 19, KartStatus.AVAILABLE, true, false));

    assertThatThrownBy(() -> rideService.start(new RideRequest(HUB_ID, KART_ID), USER_ID))
        .isInstanceOf(LowBatteryException.class)
        .hasMessage("Kart has low battery.");
    verify(hubRepository).markKartLowBattery(HUB_ID, KART_ID);
    verify(rideRepository, never()).save(any(Ride.class));
  }

  @Test
  void acceptsExactlyTwentyPercentBattery() {
    givenKart(new Kart(KART_ID, "Kart 1", 20, KartStatus.AVAILABLE, true, false));

    rideService.start(new RideRequest(HUB_ID, KART_ID), USER_ID);

    verify(rideRepository).save(any(Ride.class));
  }

  @Test
  void rejectsASecondActiveRide() {
    when(rideRepository.findActiveByUser(USER_ID))
        .thenReturn(
            List.of(
                new Ride(
                    UUID.randomUUID(),
                    USER_ID,
                    HUB_ID,
                    "kart-0",
                    java.time.Instant.now(),
                    RideStatus.ACTIVE)));

    assertThatThrownBy(() -> rideService.start(new RideRequest(HUB_ID, KART_ID), USER_ID))
        .isInstanceOf(ActiveRideException.class)
        .hasMessage("Only one active trip is allowed per user.");
    verify(hubRepository, never()).startKart(any(), any());
  }

  private void givenKart(Kart kart) {
    when(hubRepository.findById(HUB_ID))
        .thenReturn(Optional.of(new Hub(HUB_ID, "Central Hub", "Main Street", List.of(kart))));
  }

  private Booking activeBooking(UUID userId) {
    return new Booking(
        UUID.randomUUID(),
        userId,
        HUB_ID,
        KART_ID,
        java.time.Instant.now(),
        java.time.Instant.now().plusSeconds(600),
        BookingStatus.ACTIVE);
  }
}
