package pt.ua.kartylicious.bookings.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pt.ua.kartylicious.bookings.BookingRequest;
import pt.ua.kartylicious.bookings.BookingService;
import pt.ua.kartylicious.bookings.ReservationLifecycleService;
import pt.ua.kartylicious.bookings.port.BookingRepository;
import pt.ua.kartylicious.bookings.port.PaymentPort;
import pt.ua.kartylicious.hubs.FleetTelemetryService;
import pt.ua.kartylicious.hubs.TelemetryObservation;
import pt.ua.kartylicious.registration.UserAccount;
import pt.ua.kartylicious.registration.UserRole;
import pt.ua.kartylicious.registration.port.UserAccountRepository;
import pt.ua.kartylicious.rides.RideCompletionRequest;
import pt.ua.kartylicious.rides.RideRequest;
import pt.ua.kartylicious.rides.RideService;

@SpringBootTest
@Testcontainers
@Import(PersistentPaymentIT.FixedClock.class)
class PersistentPaymentIT {
  private static final Instant NOW = Instant.parse("2099-01-01T10:00:00Z");
  private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final UUID DECLINED = UUID.fromString("00000000-0000-0000-0000-000000000102");
  private static final String HUB = "central-square";
  private static final String KART = "K-101";
  private static final BigDecimal FARE = new BigDecimal("7.00");

  @Container
  private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  @Autowired private PaymentPort payments;
  @Autowired private BookingService bookings;
  @Autowired private ReservationLifecycleService lifecycle;
  @Autowired private BookingRepository bookingRepository;
  @Autowired private RideService rides;
  @Autowired private FleetTelemetryService telemetry;
  @Autowired private UserAccountRepository users;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactions;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    registry.add("payment.simulated.decline-user-id", DECLINED::toString);
    registry.add("municipal.rules.base-fare", () -> "2.00");
    registry.add("municipal.rules.per-minute-rate", () -> "0.50");
    registry.add("municipal.rules.overtime-fee", () -> "3.00");
    registry.add("municipal.rules.maximum-ride-duration-minutes", () -> "60");
  }

  @BeforeEach
  void resetSyntheticState() {
    jdbc.update("delete from payment_settlements");
    jdbc.update("delete from payment_authorizations");
    jdbc.update("delete from rides");
    jdbc.update("delete from bookings");
    jdbc.update("""
        update karts set hub_id = 'central-square', status = 'AVAILABLE', battery_level = 84,
          locked = true, active = true, critical_fault = false, telemetry_hub_id = null,
          telemetry_recorded_at = null where id = 'K-101'
        """);
    for (var user : List.of(USER, DECLINED)) {
      users.save(new UserAccount(user, "payment-" + user, user + "@example.test", "unused",
          UserRole.END_USER));
    }
  }

  @Test
  @Requirement("KARTY-38")
  void reservationAuthorizationCanSettleADifferentRideIdempotently() {
    var hold = UUID.randomUUID();
    var ride = UUID.randomUUID();
    assertThat(payments.authorizeSecurityDeposit(USER, hold)).isTrue();
    assertThat(payments.authorizeSecurityDeposit(USER, hold)).isTrue();
    assertThat(authorizationStatus(hold)).isEqualTo("AUTHORIZED");
    assertThat(payments.captureFinalCharge(USER, hold, ride, FARE)).isTrue();
    assertThat(payments.captureFinalCharge(USER, hold, ride, new BigDecimal("7.000"))).isTrue();
    assertThat(payments.captureFinalCharge(USER, hold, ride, new BigDecimal("8.00"))).isFalse();
    assertThat(payments.captureFinalCharge(USER, hold, UUID.randomUUID(), FARE)).isFalse();
    assertThat(payments.authorizeSecurityDeposit(USER, hold)).isFalse();
    payments.releaseSecurityDeposit(USER, hold);
    assertThat(authorizationStatus(hold)).isEqualTo("SETTLED");
    assertThat(amount(ride)).isEqualByComparingTo(FARE);
    assertThat(count("payment_settlements")).isEqualTo(1);
  }

  @Test
  @Requirement("KARTY-38")
  void finalSettlementRequiresAnExistingUsersAuthorization() {
    var hold = UUID.randomUUID();
    var ride = UUID.randomUUID();
    var stranger = UUID.randomUUID();
    assertThat(payments.captureFinalCharge(USER, hold, ride, FARE)).isFalse();
    assertThat(payments.authorizeSecurityDeposit(USER, hold)).isTrue();
    assertThat(payments.authorizeSecurityDeposit(stranger, hold)).isFalse();
    assertThat(payments.captureFinalCharge(stranger, hold, ride, FARE)).isFalse();
    payments.releaseSecurityDeposit(stranger, hold);
    assertThat(authorizationStatus(hold)).isEqualTo("AUTHORIZED");
    payments.releaseSecurityDeposit(USER, hold);
    payments.releaseSecurityDeposit(USER, hold);
    assertThat(authorizationStatus(hold)).isEqualTo("RELEASED");
    assertThat(payments.authorizeSecurityDeposit(USER, hold)).isFalse();
    assertThat(payments.captureFinalCharge(USER, hold, ride, FARE)).isFalse();
    assertThat(count("payment_settlements")).isZero();
  }

  @Test
  @Requirement("KARTY-38")
  void finalSettlementRejectsMissingLegacyAuthorizationReferenceAndInvalidAmounts() {
    var ride = UUID.randomUUID();
    assertThat(payments.captureFinalCharge(USER, null, ride, FARE)).isFalse();
    var hold = UUID.randomUUID();
    assertThat(payments.authorizeSecurityDeposit(USER, hold)).isTrue();
    assertThat(payments.captureFinalCharge(USER, hold, ride, null)).isFalse();
    for (var invalid : List.of("-1.00", "7.001", "10000000000.00")) {
      assertThat(payments.captureFinalCharge(USER, hold, ride, new BigDecimal(invalid))).isFalse();
    }
    assertThat(authorizationStatus(hold)).isEqualTo("AUTHORIZED");
    assertThat(count("payment_settlements")).isZero();
  }

  @Test
  @Requirement({"KARTY-36", "KARTY-37"})
  void configuredDeclineCreatesNeitherBookingNorRideNorHold() {
    assertThatThrownBy(() -> bookings.book(window(10, 20), DECLINED))
        .hasMessage("Security deposit authorization failed.");
    assertThatThrownBy(() -> rides.start(new RideRequest(HUB, KART), DECLINED))
        .hasMessage("Security deposit authorization failed.");
    assertThat(count("bookings")).isZero();
    assertThat(count("rides")).isZero();
    assertThat(count("payment_authorizations")).isZero();
    assertThat(kartStatus()).isEqualTo("AVAILABLE");
  }

  @Test
  @Requirement("KARTY-37")
  void legacyReservationWithoutAHoldCannotStartARide() {
    var hold = reserve();
    makeCurrent(hold);
    jdbc.update("delete from payment_authorizations where operation_id = ?", hold);

    assertThatThrownBy(() -> rides.start(new RideRequest(HUB, KART, hold), USER))
        .hasMessage("Security deposit authorization failed.");
    assertThat(bookingRepository.findById(hold).orElseThrow().status().name()).isEqualTo("ACTIVE");
    assertThat(kartStatus()).isEqualTo("RESERVED");
    assertThat(count("rides")).isZero();
    assertThat(count("payment_authorizations")).isZero();
  }

  @Test
  @Requirement("KARTY-38")
  void reservedRideSettlesItsBookingHoldAtTheConfiguredFare() {
    var hold = reserve();
    assertThat(authorizationStatus(hold)).isEqualTo("AUTHORIZED");
    makeCurrent(hold);
    var ride = rides.start(new RideRequest(HUB, KART, hold), USER).id();
    endAtNow(hold);
    lifecycle.expireKart(KART);
    assertThat(bookingStatus(hold)).isEqualTo("COMPLETED");
    assertThat(authorizationStatus(hold)).isEqualTo("AUTHORIZED");
    assertThat(kartStatus()).isEqualTo("IN_USE");
    prepareReturn(ride, 10);

    var completion = rides.end(ride, new RideCompletionRequest(HUB), USER);

    assertThat(completion.finalCharge()).isEqualByComparingTo(FARE);
    assertThat(authorizationStatus(hold)).isEqualTo("SETTLED");
    assertThat(jdbc.queryForObject("select authorization_id from payment_settlements where ride_id = ?",
        UUID.class, ride)).isEqualTo(hold);
    assertThat(amount(ride)).isEqualByComparingTo(FARE);
    assertThat(count("payment_authorizations")).isEqualTo(1);
  }

  @Test
  @Requirement("KARTY-38")
  void rolledBackRideCompletionRestoresAuthorizationAndParkingAndCanBeRetriedWithOvertime() {
    var ride = rides.start(new RideRequest(HUB, KART), USER).id();
    prepareReturn(ride, 61);
    transaction().executeWithoutResult(tx -> {
      assertThat(rides.end(ride, new RideCompletionRequest(HUB), USER).finalCharge())
          .isEqualByComparingTo("35.50");
      assertThat(authorizationStatus(ride)).isEqualTo("SETTLED");
      assertThat(amount(ride)).isEqualByComparingTo("35.50");
      tx.setRollbackOnly();
    });

    assertThat(authorizationStatus(ride)).isEqualTo("AUTHORIZED");
    assertThat(count("payment_settlements")).isZero();
    assertThat(kartStatus()).isEqualTo("IN_USE");
    assertThat(rides.activeRide(USER).orElseThrow().id()).isEqualTo(ride);

    rides.end(ride, new RideCompletionRequest(HUB), USER);
    assertThat(authorizationStatus(ride)).isEqualTo("SETTLED");
    assertThat(amount(ride)).isEqualByComparingTo("35.50");
    assertThat(kartStatus()).isEqualTo("AVAILABLE");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  @Requirement({"KARTY-36", "KARTY-37"})
  void databaseFailureRollsBackHoldAndNewBookingOrRide(boolean booking) {
    assertThatThrownBy(() -> transaction().executeWithoutResult(tx -> {
      var operation = booking ? reserve() : rides.start(new RideRequest(HUB, KART), USER).id();
      assertThat(authorizationStatus(operation)).isEqualTo("AUTHORIZED");
      jdbc.queryForObject("select 1 / 0", Integer.class);
    })).isInstanceOf(DataAccessException.class);

    assertThat(count("bookings")).isZero();
    assertThat(count("rides")).isZero();
    assertThat(count("payment_authorizations")).isZero();
    assertThat(kartStatus()).isEqualTo("AVAILABLE");
  }

  @Test
  @Requirement("KARTY-40")
  void relocatedKartRecoversBatteryAndReleasesOnlyWhenItsLastHoldEnds() {
    var first = reserve();
    var second = bookings.book(window(20, 30), USER).id();
    jdbc.update("update karts set hub_id = 'riverside-park', status = 'AVAILABLE' where id = ?", KART);
    telemetry.ingest(new TelemetryObservation(KART, "riverside-park", 19, true, false,
        NOW.minusSeconds(2)));
    assertThat(kartStatus()).isEqualTo("LOW_BATTERY");
    telemetry.ingest(new TelemetryObservation(KART, "riverside-park", 20, true, false,
        NOW.minusSeconds(1)));
    assertThat(kartStatus()).isEqualTo("RESERVED");

    bookings.cancel(first, USER);
    assertThat(kartStatus()).isEqualTo("RESERVED");
    assertThat(authorizationStatus(first)).isEqualTo("RELEASED");
    assertThat(authorizationStatus(second)).isEqualTo("AUTHORIZED");
    jdbc.update("update bookings set ends_at = ? where id = ?",
        Timestamp.from(NOW.minusSeconds(1)), second);
    lifecycle.expireKart(KART);
    assertThat(bookingStatus(first)).isEqualTo("CANCELLED");
    assertThat(bookingStatus(second)).isEqualTo("EXPIRED");
    assertThat(bookings.activeBookings(USER)).isEmpty();
    assertThat(authorizationStatus(second)).isEqualTo("RELEASED");
    assertThat(kartStatus()).isEqualTo("AVAILABLE");
    assertThat(jdbc.queryForObject("select hub_id from karts where id = ?", String.class, KART))
        .isEqualTo("riverside-park");
  }

  @ParameterizedTest
  @CsvSource({"RESERVED,84,false,AVAILABLE", "RESERVED,19,false,LOW_BATTERY",
      "RESERVED,84,true,IN_MAINTENANCE", "IN_MAINTENANCE,84,true,IN_MAINTENANCE",
      "IN_USE,84,false,IN_USE", "INACTIVE,84,false,INACTIVE", "RETIRED,84,false,RETIRED"})
  @Requirement("KARTY-36")
  void endingAtCurrentTimeExpiresAndReleasesExactlyOnce(
      String status, int battery, boolean fault, String expected) {
    var hold = reserve();
    endAtNow(hold);
    jdbc.update("update karts set status = ?, battery_level = ?, critical_fault = ? where id = ?",
        status, battery, fault, KART);

    lifecycle.expireKart(KART);
    assertThat(bookingStatus(hold)).isEqualTo("EXPIRED");
    assertThat(authorizationStatus(hold)).isEqualTo("RELEASED");
    assertThat(kartStatus()).isEqualTo(expected);

    var originalRelease = Timestamp.from(NOW.minusSeconds(45));
    jdbc.update("update payment_authorizations set updated_at = ? where operation_id = ?",
        originalRelease, hold);
    lifecycle.expireKart(KART);

    assertThat(jdbc.queryForObject("select updated_at from payment_authorizations where operation_id = ?",
        Timestamp.class, hold)).isEqualTo(originalRelease);
    assertThat(bookingStatus(hold)).isEqualTo("EXPIRED");
    assertThat(kartStatus()).isEqualTo(expected);
    assertThat(count("payment_authorizations")).isEqualTo(1);
  }

  @Test
  @Requirement("KARTY-36")
  void expiryPreservesFutureReservationAndItsDeposit() {
    var due = reserve();
    var future = bookings.book(window(20, 30), USER).id();
    endAtNow(due);

    lifecycle.expireKart(KART);

    assertThat(bookingStatus(due)).isEqualTo("EXPIRED");
    assertThat(authorizationStatus(due)).isEqualTo("RELEASED");
    assertThat(bookingStatus(future)).isEqualTo("ACTIVE");
    assertThat(authorizationStatus(future)).isEqualTo("AUTHORIZED");
    assertThat(kartStatus()).isEqualTo("RESERVED");
  }

  @Test
  @Requirement("KARTY-36")
  void failedExpiryRollsBackReservationPaymentAndKartAndCanBeRetried() {
    var hold = reserve();
    endAtNow(hold);

    assertThatThrownBy(() -> transaction().executeWithoutResult(tx -> {
      lifecycle.expireKart(KART);
      assertThat(bookingStatus(hold)).isEqualTo("EXPIRED");
      assertThat(authorizationStatus(hold)).isEqualTo("RELEASED");
      jdbc.queryForObject("select 1 / 0", Integer.class);
    })).isInstanceOf(DataAccessException.class);

    assertThat(bookingStatus(hold)).isEqualTo("ACTIVE");
    assertThat(authorizationStatus(hold)).isEqualTo("AUTHORIZED");
    assertThat(kartStatus()).isEqualTo("RESERVED");
    lifecycle.expireKart(KART);
    assertThat(bookingStatus(hold)).isEqualTo("EXPIRED");
    assertThat(authorizationStatus(hold)).isEqualTo("RELEASED");
    assertThat(kartStatus()).isEqualTo("AVAILABLE");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  @Requirement({"KARTY-36", "KARTY-37", "KARTY-40"})
  void concurrentExpiryAndCancellationOrStartCannotConsumeExpiredReservation(boolean cancel)
      throws Exception {
    var hold = reserve();
    endAtNow(hold);
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var expiry = executor.submit(() -> {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        lifecycle.expireKart(KART);
        return null;
      });
      var competing = executor.submit(() -> {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        if (cancel) {
          assertThatThrownBy(() -> bookings.cancel(hold, USER))
              .isInstanceOf(RuntimeException.class).hasMessageContaining("cancel");
        } else {
          assertThatThrownBy(() -> rides.start(new RideRequest(HUB, KART, hold), USER))
              .isInstanceOf(RuntimeException.class).hasMessageContaining("available");
        }
        return null;
      });
      var bothReady = ready.await(5, TimeUnit.SECONDS);
      start.countDown();
      assertThat(bothReady).isTrue();
      competing.get(10, TimeUnit.SECONDS);
      expiry.get(10, TimeUnit.SECONDS);
    }

    assertThat(bookingStatus(hold)).isEqualTo("EXPIRED");
    assertThat(authorizationStatus(hold)).isEqualTo("RELEASED");
    assertThat(kartStatus()).isEqualTo("AVAILABLE");
    assertThat(count("rides")).isZero();
  }

  @Test
  @Requirement("KARTY-38")
  void replacementAdapterCanReadAndSettlePreviouslyCommittedHold() {
    var hold = UUID.randomUUID();
    var ride = UUID.randomUUID();
    assertThat(payments.authorizeSecurityDeposit(USER, hold)).isTrue();

    var replacement = new SimulatedPaymentAdapter(
        new JdbcTemplate(jdbc.getDataSource()), Clock.fixed(NOW, ZoneOffset.UTC), "");
    assertThat(replacement.hasAuthorizedDeposit(USER, hold)).isTrue();
    transaction().executeWithoutResult(
        tx -> assertThat(replacement.captureFinalCharge(USER, hold, ride, FARE)).isTrue());
    assertThat(authorizationStatus(hold)).isEqualTo("SETTLED");
    assertThat(amount(ride)).isEqualByComparingTo(FARE);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  @Requirement("KARTY-38")
  void onlyOneConcurrentConflictingSettlementWins(boolean sameAuthorization) throws Exception {
    var firstHold = UUID.randomUUID();
    var firstRide = UUID.randomUUID();
    var secondHold = sameAuthorization ? firstHold : UUID.randomUUID();
    var secondRide = sameAuthorization ? UUID.randomUUID() : firstRide;
    assertThat(payments.authorizeSecurityDeposit(USER, firstHold)).isTrue();
    assertThat(payments.authorizeSecurityDeposit(USER, secondHold)).isTrue();

    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> captureWhenReady(ready, start, firstHold, firstRide));
      var second = executor.submit(() -> captureWhenReady(ready, start, secondHold, secondRide));
      var bothReady = ready.await(5, TimeUnit.SECONDS);
      start.countDown();
      assertThat(bothReady).isTrue();
      assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(count("payment_settlements")).isEqualTo(1);
    assertThat(count("payment_authorizations where status = 'SETTLED'")).isEqualTo(1);
  }

  private boolean captureWhenReady(CountDownLatch ready, CountDownLatch start, UUID hold, UUID ride)
      throws InterruptedException {
    ready.countDown();
    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
    return payments.captureFinalCharge(USER, hold, ride, FARE);
  }

  private TransactionTemplate transaction() {
    return new TransactionTemplate(transactions);
  }

  private BookingRequest window(long start, long end) {
    return new BookingRequest(HUB, KART, NOW.plusSeconds(start * 60), NOW.plusSeconds(end * 60));
  }

  private UUID reserve() {
    return bookings.book(window(10, 20), USER).id();
  }

  private void makeCurrent(UUID booking) {
    jdbc.update("update bookings set starts_at = ? where id = ?",
        Timestamp.from(NOW.minusSeconds(60)), booking);
  }

  private void endAtNow(UUID booking) {
    jdbc.update("update bookings set starts_at = ?, ends_at = ? where id = ?",
        Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(NOW), booking);
  }

  private String bookingStatus(UUID booking) {
    return bookingRepository.findById(booking).orElseThrow().status().name();
  }

  private void prepareReturn(UUID ride, long minutes) {
    jdbc.update("update rides set started_at = ? where id = ?",
        Timestamp.from(NOW.minusSeconds(minutes * 60)), ride);
    telemetry.ingest(new TelemetryObservation(KART, HUB, 84, false, false, NOW.minusSeconds(1)));
  }

  private String authorizationStatus(UUID hold) {
    return jdbc.queryForObject("select status from payment_authorizations where operation_id = ?",
        String.class, hold);
  }

  private String kartStatus() {
    return jdbc.queryForObject("select status from karts where id = ?", String.class, KART);
  }

  private BigDecimal amount(UUID ride) {
    return jdbc.queryForObject("select amount from payment_settlements where ride_id = ?",
        BigDecimal.class, ride);
  }

  private int count(String tableExpression) {
    return jdbc.queryForObject("select count(*) from " + tableExpression, Integer.class);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FixedClock {
    @Bean
    @Primary
    Clock paymentTestClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }
}
