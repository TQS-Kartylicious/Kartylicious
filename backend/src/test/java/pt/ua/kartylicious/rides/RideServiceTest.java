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
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.kartylicious.bookings.Booking;
import pt.ua.kartylicious.bookings.BookingStatus;
import pt.ua.kartylicious.bookings.ReservationLifecycleService;
import pt.ua.kartylicious.bookings.port.PaymentPort;
import pt.ua.kartylicious.config.MunicipalRulesProperties;
import pt.ua.kartylicious.hubs.Hub;
import pt.ua.kartylicious.hubs.Kart;
import pt.ua.kartylicious.hubs.KartStatus;
import pt.ua.kartylicious.hubs.TelemetrySnapshot;
import pt.ua.kartylicious.hubs.port.HubRepository;
import pt.ua.kartylicious.hubs.port.TelemetryPort;
import pt.ua.kartylicious.registration.UserAccount;
import pt.ua.kartylicious.registration.UserRole;
import pt.ua.kartylicious.registration.port.UserAccountRepository;
import pt.ua.kartylicious.rides.port.RideRepository;

@ExtendWith(MockitoExtension.class)
class RideServiceTest {
  private static final String HUB_ID = "hub-1";
  private static final String DESTINATION_HUB_ID = "hub-2";
  private static final String KART_ID = "kart-1";
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID RIDE_ID = UUID.randomUUID();
  private static final UUID AUTHORIZATION_ID = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2099-01-01T10:00:00Z");

  @Mock private HubRepository hubRepository;
  @Mock private ReservationLifecycleService reservationLifecycleService;
  @Mock private RideRepository rideRepository;
  @Mock private UserAccountRepository userAccountRepository;
  @Mock private PaymentPort paymentPort;
  @Mock private TelemetryPort telemetryPort;

  private RideService rideService;

  @BeforeEach
  void setUp() {
    var municipalRules = new MunicipalRulesProperties();
    municipalRules.setBaseFare(new BigDecimal("2.00"));
    municipalRules.setPerMinuteRate(new BigDecimal("0.50"));
    municipalRules.setOvertimeFee(new BigDecimal("3.00"));
    municipalRules.setMaximumRideDurationMinutes(60);
    rideService =
        new RideService(
            hubRepository,
            reservationLifecycleService,
            rideRepository,
            userAccountRepository,
            paymentPort,
            telemetryPort,
            municipalRules,
            Clock.fixed(NOW, ZoneOffset.UTC));
    lenient()
        .when(userAccountRepository.lockById(USER_ID))
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
    lenient().when(paymentPort.hasAuthorizedDeposit(any(), any())).thenReturn(true);
  }

  @Test
  @Requirement("KARTY-38")
  void endsOwnedRideAtAuthorizedHubAndChargesFinalFareAtTwentyPercentBattery() {
    givenActiveRide(NOW.minusSeconds(600));
    givenReturnConditions(20, false, true, 2, List.of());

    var response =
        rideService.end(RIDE_ID, new RideCompletionRequest(DESTINATION_HUB_ID), USER_ID);

    assertThat(response.status()).isEqualTo(RideStatus.COMPLETED.name());
    assertThat(response.kartStatus()).isEqualTo(KartStatus.AVAILABLE.name());
    assertThat(response.finalCharge()).isEqualByComparingTo("7.00");
    assertThat(response.endedAt()).isEqualTo(NOW);
    verify(telemetryPort).lockVehicle(KART_ID);
    verify(paymentPort)
        .captureFinalCharge(USER_ID, AUTHORIZATION_ID, RIDE_ID, new BigDecimal("7.00"));
    verify(hubRepository)
        .parkKartAtHub(DESTINATION_HUB_ID, KART_ID, KartStatus.AVAILABLE);

    var savedRide = ArgumentCaptor.forClass(Ride.class);
    verify(rideRepository).save(savedRide.capture());
    assertThat(savedRide.getValue().status()).isEqualTo(RideStatus.COMPLETED);
    assertThat(savedRide.getValue().endHubId()).isEqualTo(DESTINATION_HUB_ID);
  }

  @Test
  @Requirement("KARTY-38")
  void endsRideAsLowBatteryAtNineteenPercent() {
    givenActiveRide(NOW.minusSeconds(600));
    givenReturnConditions(19, false, true, 2, List.of());

    var response =
        rideService.end(RIDE_ID, new RideCompletionRequest(DESTINATION_HUB_ID), USER_ID);

    assertThat(response.kartStatus()).isEqualTo(KartStatus.LOW_BATTERY.name());
    verify(hubRepository)
        .parkKartAtHub(DESTINATION_HUB_ID, KART_ID, KartStatus.LOW_BATTERY);
  }

  @Test
  @Requirement("KARTY-38")
  void appliesConfiguredOvertimeFeeAfterMaximumRideDuration() {
    givenActiveRide(NOW.minusSeconds(61 * 60));
    givenReturnConditions(80, false, true, 2, List.of());

    var response =
        rideService.end(RIDE_ID, new RideCompletionRequest(DESTINATION_HUB_ID), USER_ID);

    assertThat(response.finalCharge()).isEqualByComparingTo("35.50");
    verify(paymentPort)
        .captureFinalCharge(USER_ID, AUTHORIZATION_ID, RIDE_ID, new BigDecimal("35.50"));
  }

  @Test
  @Requirement("KARTY-38")
  void rejectsEndWhenLatestTelemetryIsOutsideSelectedHub() {
    givenActiveRide(NOW.minusSeconds(600));
    givenReturnConditions(80, false, true, 2, List.of());
    when(telemetryPort.latestFor(KART_ID))
        .thenReturn(
            Optional.of(new TelemetrySnapshot(KART_ID, HUB_ID, 80, false, NOW)));

    assertThatThrownBy(
            () ->
                rideService.end(
                    RIDE_ID, new RideCompletionRequest(DESTINATION_HUB_ID), USER_ID))
        .isInstanceOf(UnauthorizedHubException.class)
        .hasMessage("Park at an authorized hub");

    verify(paymentPort, never()).captureFinalCharge(any(), any(), any(), any());
    verify(rideRepository, never()).save(any());
  }

  @Test
  @Requirement("KARTY-38")
  void rejectsTelemetryRecordedBeforeTheRideStarted() {
    var startedAt = NOW.minusSeconds(600);
    givenActiveRide(startedAt);
    givenReturnConditions(80, false, true, 2, List.of());
    when(telemetryPort.latestFor(KART_ID))
        .thenReturn(
            Optional.of(
                new TelemetrySnapshot(
                    KART_ID,
                    DESTINATION_HUB_ID,
                    80,
                    false,
                    startedAt.minusSeconds(1))));

    assertThatThrownBy(
            () ->
                rideService.end(
                    RIDE_ID, new RideCompletionRequest(DESTINATION_HUB_ID), USER_ID))
        .isInstanceOf(UnauthorizedHubException.class)
        .hasMessage("Park at an authorized hub");

    verify(telemetryPort, never()).lockVehicle(any());
    verify(paymentPort, never()).captureFinalCharge(any(), any(), any(), any());
  }

  @Test
  @Requirement("KARTY-38")
  void rejectsEndWhenDestinationHubIsFull() {
    givenActiveRide(NOW.minusSeconds(600));
    var parkedKart =
        new Kart("parked", "Parked kart", 80, KartStatus.AVAILABLE, true, false);
    givenReturnConditions(80, false, true, 1, List.of(parkedKart));

    assertThatThrownBy(
            () ->
                rideService.end(
                    RIDE_ID, new RideCompletionRequest(DESTINATION_HUB_ID), USER_ID))
        .isInstanceOf(HubFullException.class)
        .hasMessage("The hub is full");

    verify(telemetryPort, never()).lockVehicle(any());
    verify(paymentPort, never()).captureFinalCharge(any(), any(), any(), any());
  }

  @Test
  @Requirement("KARTY-38")
  void rejectsEndByAnotherEndUser() {
    givenActiveRide(NOW.minusSeconds(600));

    assertThatThrownBy(
            () ->
                rideService.end(
                    RIDE_ID, new RideCompletionRequest(DESTINATION_HUB_ID), UUID.randomUUID()))
        .isInstanceOf(RideAccessDeniedException.class);

    verify(hubRepository, never()).lockHub(any());
    verify(paymentPort, never()).captureFinalCharge(any(), any(), any(), any());
  }

  @Test
  @Requirement("KARTY-38")
  void duplicateCompletionDoesNotChargeAgain() {
    var completedRide =
        completedRide(NOW.minusSeconds(600), NOW.minusSeconds(60), new BigDecimal("7.00"));
    when(rideRepository.lockById(RIDE_ID)).thenReturn(Optional.of(completedRide));

    assertThatThrownBy(
            () ->
                rideService.end(
                    RIDE_ID, new RideCompletionRequest(DESTINATION_HUB_ID), USER_ID))
        .isInstanceOf(RideAlreadyCompletedException.class);

    verify(paymentPort, never()).captureFinalCharge(any(), any(), any(), any());
  }

  @Test
  @Requirement("KARTY-38")
  void paymentFailureLeavesRideAndKartStateUnchanged() {
    givenActiveRide(NOW.minusSeconds(600));
    givenReturnConditions(80, false, true, 2, List.of());
    when(paymentPort.captureFinalCharge(any(), any(), any(), any())).thenReturn(false);

    assertThatThrownBy(
            () ->
                rideService.end(
                    RIDE_ID, new RideCompletionRequest(DESTINATION_HUB_ID), USER_ID))
        .isInstanceOf(FinalPaymentException.class);

    verify(hubRepository, never()).parkKartAtHub(any(), any(), any());
    verify(rideRepository, never()).save(any());
  }

  @Test
  @Requirement("KARTY-38")
  void lockFailureDoesNotChargeOrCompleteTheRide() {
    givenActiveRide(NOW.minusSeconds(600));
    givenReturnConditions(80, false, true, 2, List.of());
    when(telemetryPort.lockVehicle(KART_ID)).thenReturn(false);

    assertThatThrownBy(
            () ->
                rideService.end(
                    RIDE_ID, new RideCompletionRequest(DESTINATION_HUB_ID), USER_ID))
        .isInstanceOf(VehicleLockException.class);

    verify(paymentPort, never()).captureFinalCharge(any(), any(), any(), any());
    verify(hubRepository, never()).parkKartAtHub(any(), any(), any());
    verify(rideRepository, never()).save(any());
  }

  @Test
  @Requirement("KARTY-38")
  void missingLegacyPaymentAuthorizationFailsWithoutCharging() {
    when(rideRepository.lockById(RIDE_ID))
        .thenReturn(
            Optional.of(
                new Ride(
                    RIDE_ID,
                    USER_ID,
                    HUB_ID,
                    KART_ID,
                    NOW.minusSeconds(600),
                    null,
                    null,
                    null,
                    null,
                    RideStatus.ACTIVE)));
    givenReturnConditions(80, false, true, 2, List.of());

    assertThatThrownBy(
            () ->
                rideService.end(
                    RIDE_ID, new RideCompletionRequest(DESTINATION_HUB_ID), USER_ID))
        .isInstanceOf(FinalPaymentException.class);

    verify(telemetryPort, never()).lockVehicle(any());
    verify(paymentPort, never()).captureFinalCharge(any(), any(), any(), any());
    verify(hubRepository, never()).parkKartAtHub(any(), any(), any());
    verify(rideRepository, never()).save(any());
  }

  @Test
  @Requirement("KARTY-38")
  void criticalFaultReturnsKartToMaintenance() {
    givenActiveRide(NOW.minusSeconds(600));
    givenReturnConditions(80, true, true, 2, List.of());

    var response =
        rideService.end(RIDE_ID, new RideCompletionRequest(DESTINATION_HUB_ID), USER_ID);

    assertThat(response.kartStatus()).isEqualTo(KartStatus.IN_MAINTENANCE.name());
    verify(hubRepository)
        .parkKartAtHub(DESTINATION_HUB_ID, KART_ID, KartStatus.IN_MAINTENANCE);
  }

  @Test
  @Requirement("KARTY-39")
  void cleanTelemetryCannotClearAReportedCriticalFaultOnReturn() {
    givenActiveRide(NOW.minusSeconds(600));
    givenReturnConditions(80, false, true, 2, List.of());
    when(hubRepository.lockKartById(KART_ID))
        .thenReturn(
            Optional.of(new Kart(KART_ID, "Kart 1", 80, KartStatus.IN_USE, true, true)));

    var response =
        rideService.end(RIDE_ID, new RideCompletionRequest(DESTINATION_HUB_ID), USER_ID);

    assertThat(response.kartStatus()).isEqualTo(KartStatus.IN_MAINTENANCE.name());
    verify(hubRepository).parkKartAtHub(DESTINATION_HUB_ID, KART_ID, KartStatus.IN_MAINTENANCE);
  }

  @Test
  @Requirement("KARTY-38")
  void inactiveKartRemainsInactiveAfterReturn() {
    givenActiveRide(NOW.minusSeconds(600));
    givenReturnConditions(80, false, false, 2, List.of());

    var response =
        rideService.end(RIDE_ID, new RideCompletionRequest(DESTINATION_HUB_ID), USER_ID);

    assertThat(response.kartStatus()).isEqualTo(KartStatus.INACTIVE.name());
    verify(hubRepository)
        .parkKartAtHub(DESTINATION_HUB_ID, KART_ID, KartStatus.INACTIVE);
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
    var booking = activeBooking(USER_ID);
    when(reservationLifecycleService.activeByKart(HUB_ID, KART_ID))
        .thenReturn(List.of(booking));

    rideService.start(new RideRequest(HUB_ID, KART_ID), USER_ID);

    verify(hubRepository).startKart(HUB_ID, KART_ID, KartStatus.RESERVED);
    verify(reservationLifecycleService).complete(booking);
    verify(paymentPort, never()).authorizeSecurityDeposit(any(), any());
    var savedRide = ArgumentCaptor.forClass(Ride.class);
    verify(rideRepository).save(savedRide.capture());
    assertThat(savedRide.getValue().paymentAuthorizationId()).isEqualTo(booking.id());
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
  void propagatesImmediateRidePersistenceFailureToRollBackTheDeposit() {
    givenKart(new Kart(KART_ID, "Kart 1", 80, KartStatus.AVAILABLE, true, false));
    doThrow(new IllegalStateException("database unavailable"))
        .when(rideRepository)
        .save(any(Ride.class));

    assertThatThrownBy(() -> rideService.start(new RideRequest(HUB_ID, KART_ID), USER_ID))
        .isInstanceOf(IllegalStateException.class);

    verify(paymentPort).authorizeSecurityDeposit(any(), any());
  }

  private void givenKart(Kart kart) {
    when(hubRepository.findById(HUB_ID))
        .thenReturn(
            Optional.of(new Hub(HUB_ID, "Central Hub", "Main Street", 10, List.of(kart))));
  }

  private void givenActiveRide(Instant startedAt) {
    when(rideRepository.lockById(RIDE_ID))
        .thenReturn(
            Optional.of(
                new Ride(
                    RIDE_ID,
                    USER_ID,
                    HUB_ID,
                    KART_ID,
                    startedAt,
                    null,
                    null,
                    null,
                    AUTHORIZATION_ID,
                    RideStatus.ACTIVE)));
  }

  private Ride completedRide(Instant startedAt, Instant endedAt, BigDecimal finalCharge) {
    return new Ride(
        RIDE_ID,
        USER_ID,
        HUB_ID,
        KART_ID,
        startedAt,
        endedAt,
        DESTINATION_HUB_ID,
        finalCharge,
        AUTHORIZATION_ID,
        RideStatus.COMPLETED);
  }

  private void givenReturnConditions(
      int batteryLevel,
      boolean criticalFault,
      boolean active,
      int capacity,
      List<Kart> parkedKarts) {
    when(hubRepository.lockHub(DESTINATION_HUB_ID))
        .thenReturn(
            Optional.of(
                new Hub(
                    DESTINATION_HUB_ID,
                    "Destination Hub",
                    "Destination Street",
                    capacity,
                    parkedKarts)));
    when(hubRepository.lockKartById(KART_ID))
        .thenReturn(
            Optional.of(
                new Kart(
                    KART_ID,
                    "Kart 1",
                    batteryLevel,
                    KartStatus.IN_USE,
                    active,
                    criticalFault)));
    when(telemetryPort.latestFor(KART_ID))
        .thenReturn(
            Optional.of(
                new TelemetrySnapshot(
                    KART_ID, DESTINATION_HUB_ID, batteryLevel, criticalFault, NOW)));
    lenient().when(telemetryPort.lockVehicle(KART_ID)).thenReturn(true);
    lenient()
        .when(paymentPort.captureFinalCharge(any(), any(), any(), any()))
        .thenReturn(true);
    lenient().when(hubRepository.parkKartAtHub(any(), any(), any())).thenReturn(true);
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
