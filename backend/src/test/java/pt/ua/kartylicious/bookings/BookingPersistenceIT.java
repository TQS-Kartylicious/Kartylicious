package pt.ua.kartylicious.bookings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import pt.ua.kartylicious.bookings.port.BookingRepository;
import pt.ua.kartylicious.bookings.port.PaymentPort;
import pt.ua.kartylicious.faults.FaultReport;
import pt.ua.kartylicious.faults.FaultService;
import pt.ua.kartylicious.registration.UserAccount;
import pt.ua.kartylicious.registration.UserRole;
import pt.ua.kartylicious.registration.port.UserAccountRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("Layer1")
class BookingPersistenceIT {

  private static final String HUB_ID = "central-square";
  private static final String FIRST_KART_ID = "K-101";
  private static final UUID FIRST_USER_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000032");

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16-alpine");

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private UserAccountRepository userAccountRepository;
  @Autowired private BookingRepository bookingRepository;
  @Autowired private BookingService bookingService;
  @Autowired private FaultService faultService;
  @Autowired private PlatformTransactionManager transactionManager;

  @MockitoSpyBean private PaymentPort paymentPort;

  @DynamicPropertySource
  static void registerDatabaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
  }

  @BeforeEach
  void resetBookingState() {
    jdbcTemplate.update("delete from payment_settlements");
    jdbcTemplate.update("delete from payment_authorizations");
    jdbcTemplate.update("delete from rides");
    jdbcTemplate.update("delete from bookings");
    jdbcTemplate.update("delete from maintenance_tickets");
    jdbcTemplate.update("delete from fault_reports");
    jdbcTemplate.update("update karts set status = 'AVAILABLE' where id in ('K-101', 'K-102')");
    jdbcTemplate.update(
        "update karts set hub_id = 'central-square', active = true, critical_fault = false, "
            + "battery_level = 84, locked = true "
            + "where id = 'K-101'");
    userAccountRepository.save(
        new UserAccount(
            FIRST_USER_ID,
            "karty-32-first-user",
            "karty-32-first-user@example.com",
            "not-used",
            UserRole.END_USER));
  }

  @Test
  @Requirement({"KARTY-36", "KARTY-38"})
  void relocatingAKartCannotPermitOverlappingReservationsOrAnImmediateRide() throws Exception {
    var original = createReservation();
    jdbcTemplate.update(
        "update karts set hub_id = 'riverside-park', status = 'AVAILABLE' where id = 'K-101'");
    jdbcTemplate.update("update hubs set capacity = capacity + 1 where id = 'riverside-park'");
    var rider = createEndUser("relocated");
    var token = endUserBearer(rider);
    var newHub = bookingJson(FIRST_KART_ID, 15, 30).replace(HUB_ID, "riverside-park");
    mockMvc.perform(post("/api/bookings").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON).content(newHub))
        .andExpect(status().isConflict());
    mockMvc.perform(post("/api/rides").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"hubId\":\"riverside-park\",\"kartId\":\"K-101\"}"))
        .andExpect(status().isConflict());
    mockMvc.perform(post("/api/bookings").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(bookingJson(FIRST_KART_ID, 30, 50).replace(HUB_ID, "riverside-park")))
        .andExpect(status().isOk());
    assertThat(bookingRepository.findById(original).orElseThrow().hubId()).isEqualTo(HUB_ID);
    assertThatCount("bookings where status = 'ACTIVE'", 2);
    assertThatCount("rides", 0);
  }

  @Test
  @Requirement("KARTY-39")
  void bookingWaitingForACriticalReportRejectsTheKartAfterReportCommit() throws Exception {
    var rider = createEndUser("report-race");
    var token = endUserBearer(rider);
    var ready = new CountDownLatch(1);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var pending = new TransactionTemplate(transactionManager).execute(transaction -> {
        faultService.report(FIRST_KART_ID, FIRST_USER_ID, FaultReport.Category.CRITICAL, "Brake jam");
        var booking = executor.submit(() -> {
          ready.countDown();
          return postBooking(token, FIRST_KART_ID, 10, 30).andReturn().getResponse().getStatus();
        });
        assertThatThrownBy(() -> {
          assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
          booking.get(300, TimeUnit.MILLISECONDS);
        }).isInstanceOf(TimeoutException.class);
        return booking;
      });
      assertThat(pending.get(10, TimeUnit.SECONDS)).isEqualTo(409);
    }
    assertKartStatus("IN_MAINTENANCE");
    assertThatCount("maintenance_tickets", 1);
    assertThatCount("bookings", 0);
    verify(paymentPort, never()).authorizeSecurityDeposit(any(), any());
  }

  @ParameterizedTest
  @CsvSource({
    "true, IN_MAINTENANCE, IN_MAINTENANCE",
    "false, RESERVED, AVAILABLE",
    "false, RETIRED, RETIRED",
    "true, INACTIVE, INACTIVE"
  })
  @Requirement({"KARTY-38", "KARTY-40"})
  void movedKartDoesNotStrandDepositsAndPreservesRestrictions(
      boolean expiry, String kartStatus, String expected) throws Exception {
    var bookingId = createReservation();
    jdbcTemplate.update(
        "update karts set hub_id = 'riverside-park', status = ? where id = ?", kartStatus, FIRST_KART_ID);
    if (expiry) {
      jdbcTemplate.update(
          "update bookings set starts_at = current_timestamp - interval '20 minutes', "
              + "ends_at = current_timestamp - interval '1 minute' where id = ?", bookingId);
      mockMvc.perform(
              get("/api/bookings/active").header("Authorization", endUserBearer(FIRST_USER_ID)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(0));
    } else {
      cancelBooking(bookingId, FIRST_USER_ID).andExpect(status().isOk());
    }
    assertThat(bookingRepository.findById(bookingId).orElseThrow().status())
        .isEqualTo(expiry ? BookingStatus.EXPIRED : BookingStatus.CANCELLED);
    assertKartStatus(expected);
    verify(paymentPort).releaseSecurityDeposit(FIRST_USER_ID, bookingId);
  }

  @Requirement("KARTY-36")
  @Test
  void confirmedBookingIsPersistedListedAsActiveAndReservesTheKart() throws Exception {
    mockMvc
        .perform(
            post("/api/bookings")
                .header("Authorization", bearer(endUserToken(FIRST_USER_ID, "END_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson(FIRST_KART_ID, 10, 30)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.message").value("Booking confirmed."));

    mockMvc
        .perform(
            get("/api/bookings/active")
                .header("Authorization", bearer(endUserToken(FIRST_USER_ID, "END_USER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].kartId").value(FIRST_KART_ID))
        .andExpect(jsonPath("$[0].status").value("ACTIVE"));

    assertThatCount("bookings", 1);
    assertThatCount("karts where id = 'K-101' and status = 'RESERVED'", 1);
  }

  @Requirement("KARTY-36")
  @Test
  void acceptsSixtyMinutesAndRejectsSixtyOneMinutes() throws Exception {
    mockMvc
        .perform(
            post("/api/bookings")
                .header("Authorization", bearer(endUserToken(FIRST_USER_ID, "END_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson(FIRST_KART_ID, 10, 70)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Booking confirmed."));

    resetBookingState();
    mockMvc
        .perform(
            post("/api/bookings")
                .header("Authorization", bearer(endUserToken(FIRST_USER_ID, "END_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson(FIRST_KART_ID, 10, 71)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Maximum permitted rental duration: 60"));

    assertThatCount("bookings", 0);
  }

  @Requirement("KARTY-36")
  @Test
  void rejectsAnOverlappingBookingButAllowsAConsecutiveBooking() throws Exception {
    var secondUserId = createEndUser("second");
    var firstToken = bearer(endUserToken(FIRST_USER_ID, "END_USER"));
    var secondToken = bearer(endUserToken(secondUserId, "END_USER"));

    postBooking(firstToken, FIRST_KART_ID, 10, 20).andExpect(status().isOk());
    postBooking(secondToken, FIRST_KART_ID, 15, 25)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("The kart is no longer available."));

    resetBookingState();
    userAccountRepository.save(
        new UserAccount(
            secondUserId,
            "karty-32-second-user",
            "karty-32-second-user@example.com",
            "not-used",
            UserRole.END_USER));

    postBooking(firstToken, FIRST_KART_ID, 10, 20).andExpect(status().isOk());
    postBooking(secondToken, FIRST_KART_ID, 20, 30)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Booking confirmed."));

    assertThatCount("bookings", 2);
  }

  @Requirement("KARTY-36")
  @Test
  void enforcesTheTwentyPercentBatteryBoundary() throws Exception {
    postBooking(bearer(endUserToken(FIRST_USER_ID, "END_USER")), "K-102", 10, 20)
        .andExpect(status().isOk());

    resetBookingState();
    postBooking(bearer(endUserToken(FIRST_USER_ID, "END_USER")), "K-103", 10, 20)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("The kart is no longer available."));

    assertThatCount("bookings", 0);
  }

  @Requirement("KARTY-36")
  @Test
  void failedDepositAuthorizationDoesNotCreateAReservation() throws Exception {
    doReturn(false).when(paymentPort).authorizeSecurityDeposit(any(), any());

    postBooking(bearer(endUserToken(FIRST_USER_ID, "END_USER")), FIRST_KART_ID, 10, 20)
        .andExpect(status().isPaymentRequired())
        .andExpect(jsonPath("$.message").value("Security deposit authorization failed."));

    assertThatCount("bookings", 0);
    assertThatCount("karts where id = 'K-101' and status = 'AVAILABLE'", 1);
  }

  @Requirement("KARTY-36")
  @Test
  void bookingEndpointsRequireAnEndUser() throws Exception {
    var operatorId = createUser("operator", UserRole.OPERATOR);

    mockMvc.perform(post("/api/bookings")).andExpect(status().isUnauthorized());
    postBooking(bearer(endUserToken(operatorId, "OPERATOR")), FIRST_KART_ID, 10, 20)
        .andExpect(status().isForbidden());
  }

  @Requirement("KARTY-36")
  @Test
  void expiredBookingIsNotListedAndReleasesItsKartAndDeposit() throws Exception {
    var bookingId = UUID.randomUUID();
    bookingRepository.save(
        new Booking(
            bookingId,
            FIRST_USER_ID,
            HUB_ID,
            FIRST_KART_ID,
            Instant.now().minusSeconds(1_200),
            Instant.now().minusSeconds(600),
            BookingStatus.ACTIVE));
    jdbcTemplate.update("update karts set status = 'RESERVED' where id = 'K-101'");

    mockMvc
        .perform(
            get("/api/bookings/active")
                .header("Authorization", bearer(endUserToken(FIRST_USER_ID, "END_USER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    assertThatCount("bookings where id = '" + bookingId + "' and status = 'EXPIRED'", 1);
    assertThatCount("karts where id = 'K-101' and status = 'AVAILABLE'", 1);
    verify(paymentPort).releaseSecurityDeposit(FIRST_USER_ID, bookingId);
  }

  @Requirement("KARTY-36")
  @Test
  void concurrentOverlappingRequestsCreateOnlyOneBooking() throws Exception {
    var secondUserId = createEndUser("concurrent-second");
    var firstToken = bearer(endUserToken(FIRST_USER_ID, "END_USER"));
    var secondToken = bearer(endUserToken(secondUserId, "END_USER"));
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(() -> concurrentBookingStatus(firstToken, ready, start));
      var second =
          executor.submit(() -> concurrentBookingStatus(secondToken, ready, start));
      ready.await();
      start.countDown();

      assertThat(java.util.List.of(first.get(), second.get()))
          .containsExactlyInAnyOrder(200, 409);
    }

    assertThatCount("bookings where kart_id = 'K-101' and status = 'ACTIVE'", 1);
  }

  @Test
  @Requirement("KARTY-40")
  void cancelsCurrentReservationAndReleasesItsKartAndBookingDepositOnlyOnce() throws Exception {
    var bookingId = createReservation();
    makeReservationCurrent(bookingId);
    jdbcTemplate.update("update karts set battery_level = 20 where id = ?", FIRST_KART_ID);

    cancelBooking(bookingId, FIRST_USER_ID)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(bookingId.toString()))
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.message").value("Reservation cancelled."));
    cancelBooking(bookingId, FIRST_USER_ID).andExpect(status().isConflict());
    startReservedRide(bookingId).andExpect(status().isConflict());

    assertThat(bookingRepository.findById(bookingId).orElseThrow().status())
        .isEqualTo(BookingStatus.CANCELLED);
    assertKartStatus("AVAILABLE");
    verify(paymentPort).authorizeSecurityDeposit(FIRST_USER_ID, bookingId);
    verify(paymentPort).authorizeSecurityDeposit(any(), any());
    verify(paymentPort).releaseSecurityDeposit(FIRST_USER_ID, bookingId);
    mockMvc
        .perform(get("/api/bookings/active").header("Authorization", endUserBearer(FIRST_USER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @Requirement("KARTY-40")
  void rejectsCancellationByAnotherEndUser() throws Exception {
    var bookingId = createReservation();
    cancelBooking(bookingId, createEndUser("other-owner")).andExpect(status().isForbidden());

    assertThat(bookingRepository.findById(bookingId).orElseThrow().status())
        .isEqualTo(BookingStatus.ACTIVE);
    assertKartStatus("RESERVED");
    verify(paymentPort, never()).releaseSecurityDeposit(any(), any());
  }

  @Test
  @Requirement("KARTY-40")
  void rejectsCancellationAfterTheReservedRideStarts() throws Exception {
    var bookingId = createReservation();
    makeReservationCurrent(bookingId);
    startReservedRide(bookingId).andExpect(status().isOk());

    cancelBooking(bookingId, FIRST_USER_ID).andExpect(status().isConflict());

    assertThat(bookingRepository.findById(bookingId).orElseThrow().status())
        .isEqualTo(BookingStatus.COMPLETED);
    assertKartStatus("IN_USE");
    verify(paymentPort, never()).releaseSecurityDeposit(any(), any());
  }

  @Test
  @Requirement("KARTY-40")
  void rejectsAnEndedReservationStillAwaitingExpiry() throws Exception {
    var bookingId = createReservation();
    jdbcTemplate.update(
        "update bookings set starts_at = current_timestamp - interval '20 minutes', "
            + "ends_at = current_timestamp - interval '1 minute' where id = ?",
        bookingId);

    cancelBooking(bookingId, FIRST_USER_ID).andExpect(status().isConflict());

    assertThat(bookingRepository.findById(bookingId).orElseThrow().status())
        .isEqualTo(BookingStatus.ACTIVE);
    verify(paymentPort, never()).releaseSecurityDeposit(any(), any());
  }

  @Test
  @Requirement("KARTY-40")
  void cancellingKeepsTheKartReservedForAnotherBooking() throws Exception {
    var bookingId = createReservation();
    var otherUser = createEndUser("next-reservation");
    postBooking(endUserBearer(otherUser), FIRST_KART_ID, 30, 50).andExpect(status().isOk());
    var nextBookingId = bookingRepository.findActiveByUser(otherUser).getFirst().id();

    cancelBooking(bookingId, FIRST_USER_ID).andExpect(status().isOk());

    assertThat(bookingRepository.findById(nextBookingId).orElseThrow().status())
        .isEqualTo(BookingStatus.ACTIVE);
    assertKartStatus("RESERVED");
    verify(paymentPort).releaseSecurityDeposit(FIRST_USER_ID, bookingId);
    verify(paymentPort, never()).releaseSecurityDeposit(otherUser, nextBookingId);
  }

  @ParameterizedTest
  @CsvSource({
    "IN_USE, true, false, 84, IN_USE",
    "INACTIVE, false, false, 84, INACTIVE",
    "RETIRED, false, false, 84, RETIRED",
    "IN_MAINTENANCE, true, true, 84, IN_MAINTENANCE",
    "LOW_BATTERY, true, false, 19, LOW_BATTERY",
    "RESERVED, true, true, 84, IN_MAINTENANCE",
    "RESERVED, true, false, 19, LOW_BATTERY",
    "RESERVED, false, false, 84, INACTIVE"
  })
  @Requirement("KARTY-40")
  void cancellationDoesNotMakeUnsafeOrInUseKartsAvailable(
      String kartStatus, boolean active, boolean criticalFault, int batteryLevel, String expected)
      throws Exception {
    var bookingId = createReservation();
    jdbcTemplate.update(
        "update karts set status = ?, active = ?, critical_fault = ?, battery_level = ? where id = ?",
        kartStatus, active, criticalFault, batteryLevel, FIRST_KART_ID);

    cancelBooking(bookingId, FIRST_USER_ID).andExpect(status().isOk());

    assertKartStatus(expected);
    verify(paymentPort).releaseSecurityDeposit(FIRST_USER_ID, bookingId);
  }

  @Test
  @Requirement("KARTY-40")
  void cancellationAndStartingTheSameReservationHaveOnlyOneWinner() throws Exception {
    var bookingId = createReservation();
    makeReservationCurrent(bookingId);
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    int cancellationStatus;
    int rideStatus;

    try (var executor = Executors.newFixedThreadPool(2)) {
      var cancellation =
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                return cancelBooking(bookingId, FIRST_USER_ID).andReturn().getResponse().getStatus();
              });
      var ride =
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                return startReservedRide(bookingId).andReturn().getResponse().getStatus();
              });
      var bothReady = ready.await(5, TimeUnit.SECONDS);
      start.countDown();
      assertThat(bothReady).isTrue();
      cancellationStatus = cancellation.get(10, TimeUnit.SECONDS);
      rideStatus = ride.get(10, TimeUnit.SECONDS);
    }

    assertThat(List.of(cancellationStatus, rideStatus)).containsExactlyInAnyOrder(200, 409);
    var booking = bookingRepository.findById(bookingId).orElseThrow();
    verify(paymentPort).authorizeSecurityDeposit(any(), any());
    if (cancellationStatus == 200) {
      assertThat(booking.status()).isEqualTo(BookingStatus.CANCELLED);
      assertKartStatus("AVAILABLE");
      assertThatCount("rides", 0);
      verify(paymentPort).releaseSecurityDeposit(FIRST_USER_ID, bookingId);
    } else {
      assertThat(booking.status()).isEqualTo(BookingStatus.COMPLETED);
      assertKartStatus("IN_USE");
      assertThatCount("rides where payment_authorization_id = '" + bookingId + "'", 1);
      verify(paymentPort, never()).releaseSecurityDeposit(any(), any());
    }
  }

  @Test
  @Requirement("KARTY-40")
  void rolledBackCancellationKeepsReservationKartAndDepositIntact() throws Exception {
    var bookingId = createReservation();

    new TransactionTemplate(transactionManager).executeWithoutResult(
        transaction -> {
          assertThat(bookingService.cancel(bookingId, FIRST_USER_ID).status()).isEqualTo("CANCELLED");
          assertThat(paymentStatus(bookingId)).isEqualTo("RELEASED");
          transaction.setRollbackOnly();
        });

    assertThat(bookingRepository.findById(bookingId).orElseThrow().status())
        .isEqualTo(BookingStatus.ACTIVE);
    assertKartStatus("RESERVED");
    assertThat(paymentStatus(bookingId)).isEqualTo("AUTHORIZED");

    cancelBooking(bookingId, FIRST_USER_ID).andExpect(status().isOk());
    assertThat(paymentStatus(bookingId)).isEqualTo("RELEASED");
  }

  private UUID createReservation() throws Exception {
    postBooking(endUserBearer(FIRST_USER_ID), FIRST_KART_ID, 10, 30).andExpect(status().isOk());
    return bookingRepository.findActiveByUser(FIRST_USER_ID).getFirst().id();
  }

  @Test
  @Requirement("KARTY-39")
  void expiryRollbackPreservesTheHoldAndCommittedExpiryReconcilesLowBattery() throws Exception {
    var bookingId = createReservation();
    jdbcTemplate.update(
        "update bookings set starts_at = current_timestamp - interval '20 minutes', "
            + "ends_at = current_timestamp - interval '1 minute' where id = ?",
        bookingId);
    jdbcTemplate.update("update karts set battery_level = 19 where id = ?", FIRST_KART_ID);

    new TransactionTemplate(transactionManager).executeWithoutResult(
        transaction -> {
          assertThat(bookingService.activeBookings(FIRST_USER_ID)).isEmpty();
          assertThat(bookingRepository.findById(bookingId).orElseThrow().status())
              .isEqualTo(BookingStatus.EXPIRED);
          assertThat(paymentStatus(bookingId)).isEqualTo("RELEASED");
          transaction.setRollbackOnly();
        });

    assertThat(bookingRepository.findById(bookingId).orElseThrow().status())
        .isEqualTo(BookingStatus.ACTIVE);
    assertKartStatus("RESERVED");
    assertThat(paymentStatus(bookingId)).isEqualTo("AUTHORIZED");

    assertThat(bookingService.activeBookings(FIRST_USER_ID)).isEmpty();
    assertThat(bookingRepository.findById(bookingId).orElseThrow().status())
        .isEqualTo(BookingStatus.EXPIRED);
    assertKartStatus("LOW_BATTERY");
    assertThat(paymentStatus(bookingId)).isEqualTo("RELEASED");
  }

  private String paymentStatus(UUID operationId) {
    return jdbcTemplate.queryForObject(
        "select status from payment_authorizations where operation_id = ?", String.class, operationId);
  }

  private void makeReservationCurrent(UUID bookingId) {
    jdbcTemplate.update(
        "update bookings set starts_at = current_timestamp - interval '1 minute', "
            + "ends_at = current_timestamp + interval '19 minutes' where id = ?",
        bookingId);
  }

  private ResultActions cancelBooking(UUID bookingId, UUID userId) throws Exception {
    return mockMvc.perform(
        patch("/api/bookings/{bookingId}", bookingId)
            .header("Authorization", endUserBearer(userId))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"CANCELLED\"}"));
  }

  private ResultActions startReservedRide(UUID bookingId) throws Exception {
    return mockMvc.perform(
        post("/api/rides")
            .header("Authorization", endUserBearer(FIRST_USER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"hubId":"%s","kartId":"%s","bookingId":"%s"}
                """
                .formatted(HUB_ID, FIRST_KART_ID, bookingId)));
  }

  private String endUserBearer(UUID userId) {
    return bearer(endUserToken(userId, "END_USER"));
  }

  private void assertKartStatus(String expected) {
    assertThat(
            jdbcTemplate.queryForObject(
                "select status from karts where id = ?", String.class, FIRST_KART_ID))
        .isEqualTo(expected);
  }

  private int concurrentBookingStatus(String token, CountDownLatch ready, CountDownLatch start)
      throws Exception {
    ready.countDown();
    start.await();
    return postBooking(token, FIRST_KART_ID, 10, 30)
        .andReturn()
        .getResponse()
        .getStatus();
  }

  private org.springframework.test.web.servlet.ResultActions postBooking(
      String token, String kartId, long startMinute, long endMinute) throws Exception {
    return mockMvc.perform(
        post("/api/bookings")
            .header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(bookingJson(kartId, startMinute, endMinute)));
  }

  private String bookingJson(String kartId, long startMinute, long endMinute) {
    var startsAt = Instant.parse("2099-01-01T10:00:00Z").plusSeconds(startMinute * 60);
    var endsAt = Instant.parse("2099-01-01T10:00:00Z").plusSeconds(endMinute * 60);
    return """
        {"hubId":"%s","kartId":"%s","startsAt":"%s","endsAt":"%s"}
        """
        .formatted(HUB_ID, kartId, startsAt, endsAt);
  }

  private UUID createEndUser(String name) {
    return createUser(name, UserRole.END_USER);
  }

  private UUID createUser(String name, UserRole role) {
    var id = UUID.randomUUID();
    userAccountRepository.save(
        new UserAccount(
            id,
            "karty-32-" + name + "-" + id,
            "karty-32-" + name + "-" + id + "@example.com",
            "not-used",
            role));
    return id;
  }

  private String endUserToken(UUID userId, String role) {
    var now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .issuer("kartylicious-test")
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .claim("role", role)
            .build();
    return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }

  private void assertThatCount(String tableExpression, int expected) {
    var sql = "select count(*) from " + tableExpression;
    org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(sql, Integer.class))
        .isEqualTo(expected);
  }
}
