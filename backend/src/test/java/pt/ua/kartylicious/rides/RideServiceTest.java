package pt.ua.kartylicious.rides;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import pt.ua.kartylicious.bookings.ReservationLifecycleService;
import pt.ua.kartylicious.bookings.port.PaymentPort;
import pt.ua.kartylicious.hubs.Hub;
import pt.ua.kartylicious.hubs.Kart;
import pt.ua.kartylicious.hubs.KartStatus;
import pt.ua.kartylicious.hubs.port.HubRepository;
import pt.ua.kartylicious.registration.UserAccount;
import pt.ua.kartylicious.registration.UserRole;
import pt.ua.kartylicious.registration.port.UserAccountRepository;
import pt.ua.kartylicious.rides.port.RideRepository;

@ExtendWith(MockitoExtension.class)
class RideServiceTest {
  private static final String HUB_ID = "hub-1";
  private static final String KART_ID = "kart-1";
  private static final UUID USER_ID = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2099-01-01T10:00:00Z");

  @Mock private HubRepository hubRepository;
  @Mock private ReservationLifecycleService reservationLifecycleService;
  @Mock private RideRepository rideRepository;
  @Mock private UserAccountRepository userAccountRepository;
  @Mock private PaymentPort paymentPort;

  private RideService rideService;

  @BeforeEach
  void setUp() {
    rideService =
        new RideService(
            hubRepository,
            reservationLifecycleService,
            rideRepository,
            userAccountRepository,
            paymentPort,
            Clock.fixed(NOW, ZoneOffset.UTC));
    when(userAccountRepository.lockById(USER_ID))
        .thenReturn(
            Optional.of(
                new UserAccount(
                    USER_ID, "rider", "rider@example.com", "hash", UserRole.END_USER)));
    lenient().when(rideRepository.findActiveByUser(USER_ID)).thenReturn(List.of());
    lenient()
        .when(reservationLifecycleService.activeByKart(HUB_ID, KART_ID))
        .thenReturn(List.of());
    lenient()
        .when(hubRepository.startKart(HUB_ID, KART_ID, KartStatus.AVAILABLE))
        .thenReturn(true);
    lenient()
        .when(hubRepository.startKart(HUB_ID, KART_ID, KartStatus.RESERVED))
        .thenReturn(true);
    lenient().when(paymentPort.authorizeSecurityDeposit(any(), any())).thenReturn(true);
  }

  @Test
  @Requirement("KARTY-37")
  void startsAnAvailableRideAndStartsTheTripTimer() {
    givenKart(new Kart(KART_ID, "Kart 1", 80, KartStatus.AVAILABLE, true, false));

    var response = rideService.start(new RideRequest(HUB_ID, KART_ID), USER_ID);

    assertThat(response.status()).isEqualTo(RideStatus.ACTIVE.name());
    assertThat(response.message()).isEqualTo("Ride started.");
    assertThat(response.startedAt()).isNotNull();
    verify(hubRepository).startKart(HUB_ID, KART_ID, KartStatus.AVAILABLE);
    verify(paymentPort).authorizeSecurityDeposit(any(), any());
    verify(rideRepository).save(any(Ride.class));
  }

  @Test
  @Requirement("KARTY-37")
  void startsAReservedRideOnlyForTheUserWhoReservedIt() {
    givenKart(new Kart(KART_ID, "Kart 1", 80, KartStatus.RESERVED, true, false));
    when(reservationLifecycleService.activeByKart(HUB_ID, KART_ID))
        .thenReturn(List.of(activeBooking(USER_ID)));

    rideService.start(new RideRequest(HUB_ID, KART_ID), USER_ID);

    verify(hubRepository).startKart(HUB_ID, KART_ID, KartStatus.RESERVED);
    verify(reservationLifecycleService).complete(any(Booking.class));
    verify(paymentPort, never()).authorizeSecurityDeposit(any(), any());
  }

  @Test
  @Requirement("KARTY-37")
  void startsAReservedRideAtTheExactReservationStartBoundary() {
    givenKart(new Kart(KART_ID, "Kart 1", 80, KartStatus.RESERVED, true, false));
    var booking =
        new Booking(
            UUID.randomUUID(),
            USER_ID,
            HUB_ID,
            KART_ID,
            NOW,
            NOW.plusSeconds(600),
            BookingStatus.ACTIVE);
    when(reservationLifecycleService.activeByKart(HUB_ID, KART_ID))
        .thenReturn(List.of(booking));

    rideService.start(new RideRequest(HUB_ID, KART_ID), USER_ID);

    verify(hubRepository).startKart(HUB_ID, KART_ID, KartStatus.RESERVED);
    verify(reservationLifecycleService).complete(booking);
  }

  @Test
  @Requirement("KARTY-37")
  void rejectsAReservedRideOwnedByAnotherUser() {
    givenKart(new Kart(KART_ID, "Kart 1", 80, KartStatus.RESERVED, true, false));
    when(reservationLifecycleService.activeByKart(HUB_ID, KART_ID))
        .thenReturn(List.of(activeBooking(UUID.randomUUID())));

    assertThatThrownBy(() -> rideService.start(new RideRequest(HUB_ID, KART_ID), USER_ID))
        .isInstanceOf(RideKartUnavailableException.class);
    verify(hubRepository, never()).startKart(any(), any(), any());
  }

  @Test
  @Requirement("KARTY-37")
  void rejectsNineteenPercentAndFlagsTheKartAsLowBattery() {
    givenKart(new Kart(KART_ID, "Kart 1", 19, KartStatus.AVAILABLE, true, false));

    assertThatThrownBy(() -> rideService.start(new RideRequest(HUB_ID, KART_ID), USER_ID))
        .isInstanceOf(LowBatteryException.class)
        .hasMessage("Kart has low battery.");
    verify(hubRepository).markKartLowBattery(HUB_ID, KART_ID);
    verify(rideRepository, never()).save(any(Ride.class));
  }

  @Test
  @Requirement("KARTY-37")
  void acceptsExactlyTwentyPercentBattery() {
    givenKart(new Kart(KART_ID, "Kart 1", 20, KartStatus.AVAILABLE, true, false));

    rideService.start(new RideRequest(HUB_ID, KART_ID), USER_ID);

    verify(rideRepository).save(any(Ride.class));
  }

  @Test
  @Requirement("KARTY-37")
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
    verify(hubRepository, never()).startKart(any(), any(), any());
  }

  @Test
  @Requirement("KARTY-37")
  void rejectsARideWhenTheLockedUserAccountIsInactive() {
    when(userAccountRepository.lockById(USER_ID))
        .thenReturn(
            Optional.of(
                new UserAccount(
                    USER_ID,
                    "rider",
                    "rider@example.com",
                    "hash",
                    UserRole.END_USER,
                    false)));

    assertThatThrownBy(() -> rideService.start(new RideRequest(HUB_ID, KART_ID), USER_ID))
        .isInstanceOf(RideValidationException.class)
        .hasMessage("User account is inactive.");

    verify(hubRepository, never()).startKart(any(), any(), any());
  }

  @Test
  @Requirement("KARTY-37")
  void rejectsAReservedRideBeforeItsReservationWindow() {
    givenKart(new Kart(KART_ID, "Kart 1", 80, KartStatus.RESERVED, true, false));
    var futureBooking =
        new Booking(
            UUID.randomUUID(),
            USER_ID,
            HUB_ID,
            KART_ID,
            NOW.plusSeconds(60),
            NOW.plusSeconds(600),
            BookingStatus.ACTIVE);
    when(reservationLifecycleService.activeByKart(HUB_ID, KART_ID))
        .thenReturn(List.of(futureBooking));

    assertThatThrownBy(() -> rideService.start(new RideRequest(HUB_ID, KART_ID), USER_ID))
        .isInstanceOf(RideKartUnavailableException.class);

    verify(hubRepository, never()).startKart(any(), any(), any());
  }

  @Test
  @Requirement("KARTY-37")
  void rejectsAnImmediateRideWhenDepositAuthorizationFails() {
    givenKart(new Kart(KART_ID, "Kart 1", 80, KartStatus.AVAILABLE, true, false));
    when(paymentPort.authorizeSecurityDeposit(any(), any())).thenReturn(false);

    assertThatThrownBy(() -> rideService.start(new RideRequest(HUB_ID, KART_ID), USER_ID))
        .isInstanceOf(RideDepositAuthorizationException.class)
        .hasMessage("Security deposit authorization failed.");

    verify(rideRepository, never()).save(any());
  }

  @Test
  @Requirement("KARTY-37")
  void releasesImmediateRideDepositWhenPersistenceFails() {
    givenKart(new Kart(KART_ID, "Kart 1", 80, KartStatus.AVAILABLE, true, false));
    doThrow(new IllegalStateException("database unavailable"))
        .when(rideRepository)
        .save(any(Ride.class));

    assertThatThrownBy(() -> rideService.start(new RideRequest(HUB_ID, KART_ID), USER_ID))
        .isInstanceOf(IllegalStateException.class);

    verify(paymentPort).releaseSecurityDeposit(any(), any());
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
        NOW.minusSeconds(60),
        NOW.plusSeconds(600),
        BookingStatus.ACTIVE);
  }
}
