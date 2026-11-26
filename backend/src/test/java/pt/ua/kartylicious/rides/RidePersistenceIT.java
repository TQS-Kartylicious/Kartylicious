package pt.ua.kartylicious.rides;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pt.ua.kartylicious.bookings.Booking;
import pt.ua.kartylicious.bookings.BookingStatus;
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
class RidePersistenceIT {

  private static final String HUB_ID = "central-square";
  private static final UUID USER_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000033");

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16-alpine");

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private BookingRepository bookingRepository;
  @Autowired private UserAccountRepository userAccountRepository;
  @Autowired private FaultService faultService;
  @Autowired private PlatformTransactionManager transactionManager;
  @MockitoBean private PaymentPort paymentPort;

  @DynamicPropertySource
  static void registerDatabaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    registry.add("municipal.rules.base-fare", () -> "2.00");
    registry.add("municipal.rules.per-minute-rate", () -> "0.50");
    registry.add("municipal.rules.overtime-fee", () -> "3.00");
    registry.add("municipal.rules.maximum-ride-duration-minutes", () -> "60");
  }

  @BeforeEach
  void resetRideState() {
    jdbcTemplate.update("delete from maintenance_tickets");
    jdbcTemplate.update("delete from fault_reports");
    jdbcTemplate.update("delete from rides");
    jdbcTemplate.update("delete from bookings");
    jdbcTemplate.update(
        "update karts set hub_id = case "
            + "when id like 'K-1%' then 'central-square' "
            + "when id like 'K-2%' then 'riverside-park' "
            + "else 'university-gardens' end, "
            + "battery_level = case id "
            + "when 'K-101' then 84 when 'K-102' then 20 when 'K-103' then 19 "
            + "when 'K-104' then 71 when 'K-105' then 63 when 'K-106' then 92 "
            + "when 'K-201' then 18 when 'K-202' then 66 when 'K-301' then 56 "
            + "when 'K-302' then 47 else 73 end, "
            + "critical_fault = id = 'K-303'");
    jdbcTemplate.update(
        "update karts set status = case id "
            + "when 'K-103' then 'LOW_BATTERY' "
            + "when 'K-104' then 'IN_USE' "
            + "when 'K-105' then 'RESERVED' "
            + "when 'K-106' then 'IN_MAINTENANCE' "
            + "else 'AVAILABLE' end, locked = true, telemetry_hub_id = null, "
            + "telemetry_recorded_at = null");
    jdbcTemplate.update(
        "update hubs h set capacity = "
            + "(select count(*) from karts k where k.hub_id = h.id)");
    userAccountRepository.save(
        new UserAccount(
            USER_ID,
            "karty-33-end-user",
            "karty-33-end-user@example.com",
            "not-used",
            UserRole.END_USER));
    when(paymentPort.authorizeSecurityDeposit(any(), any())).thenReturn(true);
    when(paymentPort.hasAuthorizedDeposit(any(), any())).thenReturn(true);
    when(paymentPort.captureFinalCharge(any(), any(), any(), any())).thenReturn(true);
  }

  @Test
  @Requirement("KARTY-38")
  void activeRideReadReturnsOnlyTheRidersPersistedActiveRide() throws Exception {
    fetchFaultData("/api/rides/active", USER_ID, "END_USER").andExpect(status().isNoContent());
    startRide("K-101", USER_ID).andExpect(status().isOk());
    var rideId = activeRideId(USER_ID);
    fetchFaultData("/api/rides/active", USER_ID, "END_USER")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(rideId.toString()))
        .andExpect(jsonPath("$.hubId").value(HUB_ID))
        .andExpect(jsonPath("$.kartId").value("K-101"))
        .andExpect(jsonPath("$.startedAt").isNotEmpty())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
    fetchFaultData("/api/rides/active", createFaultTestUser(UserRole.END_USER), "END_USER")
        .andExpect(status().isNoContent());

    reportTelemetry("K-101", HUB_ID, 84, false);
    endRide(rideId, USER_ID, HUB_ID).andExpect(status().isOk());
    fetchFaultData("/api/rides/active", USER_ID, "END_USER").andExpect(status().isNoContent());
  }

  @ParameterizedTest
  @ValueSource(strings = {"AVAILABLE", "IN_USE", "IN_MAINTENANCE", "INACTIVE", "RETIRED"})
  @Requirement("KARTY-38")
  void lowBatteryPersistsOnlyForEligibleKarts(String initialStatus) throws Exception {
    jdbcTemplate.update("update karts set battery_level = 19, status = ? where id = 'K-101'",
        initialStatus);

    var result = startRide("K-101", USER_ID).andExpect(status().isConflict());
    var expected = initialStatus.equals("AVAILABLE") ? "LOW_BATTERY" : initialStatus;
    if (initialStatus.equals("AVAILABLE")) {
      result.andExpect(jsonPath("$.message").value("Kart has low battery."));
    }
    assertThat(jdbcTemplate.queryForObject("select status from karts where id = 'K-101'",
        String.class)).isEqualTo(expected);
    assertThat(count("rides")).isZero();
    verify(paymentPort, never()).authorizeSecurityDeposit(any(), any());
  }

  @Test
  @Requirement("KARTY-38")
  void twoConsecutiveReservationsCanBothStartAndCompleteAtTheirPickupHub() throws Exception {
    var nextRider = createFaultTestUser(UserRole.END_USER);
    var boundary = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(1200).plusNanos(750);
    var first = reserve(USER_ID, boundary.minusSeconds(600), boundary);
    var second = reserve(nextRider, boundary, boundary.plusSeconds(600));
    jdbcTemplate.update("update bookings set starts_at = current_timestamp - interval '1 minute' "
        + "where id = ?", first);

    startReservation(USER_ID, first).andExpect(status().isOk());
    var firstRide = activeRideId(USER_ID);
    reportTelemetry("K-101", HUB_ID, 84, false);
    endRide(firstRide, USER_ID, HUB_ID)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.kartStatus").value("RESERVED"));
    assertThat(bookingRepository.findById(second).orElseThrow().status())
        .isEqualTo(BookingStatus.ACTIVE);

    var now = Instant.now().minusSeconds(1);
    jdbcTemplate.update("update bookings set ends_at = ? where id = ?", Timestamp.from(now), first);
    jdbcTemplate.update("update bookings set starts_at = ? where id = ?", Timestamp.from(now), second);
    startReservation(nextRider, second).andExpect(status().isOk());
    var secondRide = activeRideId(nextRider);
    reportTelemetry("K-101", HUB_ID, 84, false);
    endRide(secondRide, nextRider, HUB_ID)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.kartStatus").value("AVAILABLE"));

    assertThat(count("bookings where status = 'COMPLETED'")).isEqualTo(2);
    assertThat(count("rides where status = 'COMPLETED'")).isEqualTo(2);
    verify(paymentPort, times(2)).authorizeSecurityDeposit(any(), any());
    verify(paymentPort).captureFinalCharge(
        org.mockito.ArgumentMatchers.eq(nextRider), org.mockito.ArgumentMatchers.eq(second),
        org.mockito.ArgumentMatchers.eq(secondRide), any());
  }

  private UUID reserve(UUID userId, Instant startsAt, Instant endsAt) throws Exception {
    mockMvc.perform(
            post("/api/bookings")
                .header("Authorization", "Bearer " + token(userId, "END_USER"))
                .contentType("application/json")
                .content("""
                    {"hubId":"central-square","kartId":"K-101","startsAt":"%s","endsAt":"%s"}
                    """.formatted(startsAt, endsAt)))
        .andExpect(status().isOk());
    return bookingRepository.findActiveByUser(userId).getFirst().id();
  }

  private ResultActions startReservation(UUID userId, UUID bookingId) throws Exception {
    return mockMvc.perform(
        post("/api/rides")
            .header("Authorization", "Bearer " + token(userId, "END_USER"))
            .contentType("application/json")
            .content("""
                {"hubId":"central-square","kartId":"K-101","bookingId":"%s"}
                """.formatted(bookingId)));
  }

  @Test
  @Requirement("KARTY-39")
  void normalReportIsListedForOperatorsAndLeavesTheKartAvailable() throws Exception {
    submitFault(USER_ID, "END_USER", faultJson("K-101", "NORMAL", "\"Seat needs cleaning\""))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.message").value("Report submitted"));

    var operator = createFaultTestUser(UserRole.OPERATOR);
    fetchFaultData("/api/fault-reports", operator, "OPERATOR")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].kartId").value("K-101"))
        .andExpect(jsonPath("$[0].userId").value(USER_ID.toString()))
        .andExpect(jsonPath("$[0].description").value("Seat needs cleaning"))
        .andExpect(jsonPath("$[0].category").value("NORMAL"))
        .andExpect(jsonPath("$[0].reportedAt").isNotEmpty());
    assertThat(count("maintenance_tickets")).isZero();
    assertThat(count("karts where id = 'K-101' and status = 'AVAILABLE' and not critical_fault"))
        .isEqualTo(1);
  }

  @Test
  @Requirement("KARTY-39")
  void criticalReportOpensUrgentTicketAndBlocksBookingAndStarting() throws Exception {
    submitFault(USER_ID, "END_USER", faultJson("K-101", "CRITICAL", "\"Steering jammed\""))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.message").value("Report submitted"));

    var operator = createFaultTestUser(UserRole.OPERATOR);
    var reportId = jdbcTemplate.queryForObject(
        "select id from fault_reports where category = 'CRITICAL'", UUID.class);
    fetchFaultData("/api/maintenance-tickets", operator, "OPERATOR")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].reportId").value(reportId.toString()))
        .andExpect(jsonPath("$[0].kartId").value("K-101"))
        .andExpect(jsonPath("$[0].priority").value("URGENT"))
        .andExpect(jsonPath("$[0].status").value("OPEN"));

    var otherRider = createFaultTestUser(UserRole.END_USER);
    mockMvc.perform(
            post("/api/bookings")
                .header("Authorization", "Bearer " + token(otherRider, "END_USER"))
                .contentType("application/json")
                .content("""
                    {"hubId":"central-square","kartId":"K-101",
                     "startsAt":"2099-01-01T10:00:00Z","endsAt":"2099-01-01T10:20:00Z"}
                    """))
        .andExpect(status().isConflict());
    startRide("K-101", otherRider).andExpect(status().isConflict());
    assertThat(count("karts where id = 'K-101' and status = 'IN_MAINTENANCE' and critical_fault"))
        .isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "\"\"", "\"   \""})
  @Requirement("KARTY-39")
  void rejectsMissingEmptyAndBlankDescriptions(String description) throws Exception {
    submitFault(USER_ID, "END_USER", faultJson("K-101", "CRITICAL", description))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Describe the issue"));
    assertThat(count("fault_reports")).isZero();
    assertThat(count("karts where id = 'K-101' and status = 'AVAILABLE' and not critical_fault"))
        .isEqualTo(1);
  }

  @Test
  @Requirement("KARTY-39")
  void rejectsUnknownCategoryAndKart() throws Exception {
    submitFault(USER_ID, "END_USER", faultJson("K-101", "OTHER", "\"Fault\""))
        .andExpect(status().isBadRequest());
    submitFault(USER_ID, "END_USER", faultJson("unknown", "CRITICAL", "\"Fault\""))
        .andExpect(status().isNotFound());
    assertThat(count("fault_reports")).isZero();
    assertThat(count("maintenance_tickets")).isZero();
  }

  @Test
  @Requirement("KARTY-39")
  void enforcesSubmissionAndOperatorReadRoles() throws Exception {
    var body = faultJson("K-101", "NORMAL", "\"Fault\"");
    mockMvc.perform(post("/api/fault-reports").contentType("application/json").content(body))
        .andExpect(status().isUnauthorized());
    for (var role : List.of(UserRole.OPERATOR, UserRole.MUNICIPAL_WORKER, UserRole.ADMIN)) {
      var staff = createFaultTestUser(role);
      submitFault(staff, role.name(), body).andExpect(status().isForbidden());
      if (role != UserRole.OPERATOR) {
        fetchFaultData("/api/fault-reports", staff, role.name()).andExpect(status().isForbidden());
        fetchFaultData("/api/maintenance-tickets", staff, role.name())
            .andExpect(status().isForbidden());
      }
    }
    for (var path : List.of("/api/fault-reports", "/api/maintenance-tickets")) {
      fetchFaultData(path, USER_ID, "END_USER").andExpect(status().isForbidden());
      mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
    assertThat(count("fault_reports")).isZero();
  }

  @Test
  @Requirement("KARTY-39")
  void criticalFaultDuringRideKeepsKartInUseUntilItReturnsToMaintenance() throws Exception {
    startRide("K-101", USER_ID).andExpect(status().isOk());
    var rideId = activeRideId(USER_ID);
    reportTelemetry("K-101", HUB_ID, 84, false);
    submitFault(USER_ID, "END_USER", faultJson("K-101", "CRITICAL", "\"Braking issue\""))
        .andExpect(status().isCreated());

    assertThat(count("karts where id = 'K-101' and status = 'IN_USE' and critical_fault"))
        .isEqualTo(1);
    endRide(rideId, USER_ID, HUB_ID)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.kartStatus").value("IN_MAINTENANCE"));
    assertThat(count("karts where id = 'K-101' and status = 'IN_MAINTENANCE' and critical_fault"))
        .isEqualTo(1);
  }

  @Test
  @Requirement("KARTY-39")
  void criticalReportWaitsForTheRideStartLockAndPreservesTheStartedRide() throws Exception {
    var rideStarted = new CountDownLatch(1);
    var reporting = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var ride = executor.submit(() -> new TransactionTemplate(transactionManager).execute(
          transaction -> {
            try {
              startRide("K-101", USER_ID).andExpect(status().isOk());
              rideStarted.countDown();
              assertThat(reporting.await(10, TimeUnit.SECONDS)).isTrue();
            } catch (Exception exception) {
              throw new IllegalStateException(exception);
            }
            return null;
          }));
      var fault = executor.submit(() -> {
        assertThat(rideStarted.await(10, TimeUnit.SECONDS)).isTrue();
        reporting.countDown();
        submitFault(USER_ID, "END_USER", faultJson("K-101", "CRITICAL", "\"Braking issue\""))
            .andExpect(status().isCreated());
        return null;
      });
      ride.get(15, TimeUnit.SECONDS);
      fault.get(15, TimeUnit.SECONDS);
    }
    assertThat(count("rides where status = 'ACTIVE'")).isEqualTo(1);
    assertThat(count("karts where id = 'K-101' and status = 'IN_USE' and critical_fault"))
        .isEqualTo(1);
    assertThat(count("maintenance_tickets")).isEqualTo(1);
  }

  @Test
  @Requirement("KARTY-39")
  void rollbackRemovesCriticalReportAndTicketAndRestoresTheKart() {
    new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
      faultService.report("K-101", USER_ID, FaultReport.Category.CRITICAL, "Wheel damaged");
      assertThat(faultService.reports()).hasSize(1);
      assertThat(faultService.tickets()).hasSize(1);
      assertThat(count("karts where id = 'K-101' and status = 'IN_MAINTENANCE' and critical_fault"))
          .isEqualTo(1);
      transaction.setRollbackOnly();
    });

    assertThat(count("fault_reports")).isZero();
    assertThat(count("maintenance_tickets")).isZero();
    assertThat(count("karts where id = 'K-101' and status = 'AVAILABLE' and not critical_fault"))
        .isEqualTo(1);
  }

  private UUID createFaultTestUser(UserRole role) {
    var id = UUID.randomUUID();
    userAccountRepository.save(
        new UserAccount(id, "fault-" + id, id + "@example.test", "unused", role));
    return id;
  }

  private ResultActions submitFault(UUID userId, String role, String body) throws Exception {
    return mockMvc.perform(
        post("/api/fault-reports")
            .header("Authorization", "Bearer " + token(userId, role))
            .contentType("application/json")
            .content(body));
  }

  private ResultActions fetchFaultData(String path, UUID userId, String role) throws Exception {
    return mockMvc.perform(get(path).header("Authorization", "Bearer " + token(userId, role)));
  }

  private String faultJson(String kartId, String category, String description) {
    return """
        {"kartId":"%s","category":"%s","description":%s}
        """.formatted(kartId, category, description);
  }

  @Requirement("KARTY-38")
  @Test
  void completesRideLocksKartAndCapturesFinalCharge() throws Exception {
    startRide("K-102", USER_ID).andExpect(status().isOk());
    var rideId = activeRideId(USER_ID);
    reportTelemetry("K-102", HUB_ID, 20, false);

    endRide(rideId, USER_ID, HUB_ID)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.kartStatus").value("AVAILABLE"))
        .andExpect(jsonPath("$.finalCharge").isNumber())
        .andExpect(jsonPath("$.message").value("Ride completed."));

    assertThat(count("rides where id = '" + rideId + "' and status = 'COMPLETED'"))
        .isEqualTo(1);
    assertThat(
            count(
                "karts where id = 'K-102' and status = 'AVAILABLE' and locked = true "
                    + "and hub_id = 'central-square'"))
        .isEqualTo(1);
    verify(paymentPort)
        .captureFinalCharge(any(UUID.class), any(UUID.class), any(UUID.class), any());
  }

  @Requirement("KARTY-38")
  @Test
  void usesLatestBatteryBoundaryWhenCompletingRide() throws Exception {
    startRide("K-101", USER_ID).andExpect(status().isOk());
    var firstRideId = activeRideId(USER_ID);
    reportTelemetry("K-101", HUB_ID, 19, false);

    endRide(firstRideId, USER_ID, HUB_ID)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.kartStatus").value("LOW_BATTERY"));
    assertThat(count("karts where id = 'K-101' and status = 'LOW_BATTERY'"))
        .isEqualTo(1);

    jdbcTemplate.update("delete from rides");
    jdbcTemplate.update(
        "update karts set status = 'AVAILABLE', battery_level = 20, locked = true "
            + "where id = 'K-101'");
    startRide("K-101", USER_ID).andExpect(status().isOk());
    var secondRideId = activeRideId(USER_ID);
    reportTelemetry("K-101", HUB_ID, 20, false);

    endRide(secondRideId, USER_ID, HUB_ID)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.kartStatus").value("AVAILABLE"));
  }

  @Requirement("KARTY-38")
  @Test
  void rejectsUnauthorizedAndFullDestinationHubs() throws Exception {
    startRide("K-101", USER_ID).andExpect(status().isOk());
    var rideId = activeRideId(USER_ID);
    reportTelemetry("K-101", HUB_ID, 84, false);

    endRide(rideId, USER_ID, "riverside-park")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Park at an authorized hub"));

    var occupancy =
        jdbcTemplate.queryForObject(
            "select count(*) from karts where hub_id = ? and status <> 'IN_USE'",
            Integer.class,
            HUB_ID);
    jdbcTemplate.update("update hubs set capacity = ? where id = ?", occupancy, HUB_ID);

    endRide(rideId, USER_ID, HUB_ID)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("The hub is full"));
    assertThat(count("rides where id = '" + rideId + "' and status = 'ACTIVE'"))
        .isEqualTo(1);
  }

  @Requirement("KARTY-38")
  @Test
  void rejectsAnotherUserAndDuplicateCompletionWithoutDuplicateCharge() throws Exception {
    startRide("K-101", USER_ID).andExpect(status().isOk());
    var rideId = activeRideId(USER_ID);
    reportTelemetry("K-101", HUB_ID, 84, false);
    var otherUserId = UUID.randomUUID();
    userAccountRepository.save(
        new UserAccount(
            otherUserId,
            "karty-38-other-user",
            "karty-38-other-user@example.com",
            "not-used",
            UserRole.END_USER));

    endRide(rideId, otherUserId, HUB_ID).andExpect(status().isForbidden());
    endRide(rideId, USER_ID, HUB_ID).andExpect(status().isOk());
    endRide(rideId, USER_ID, HUB_ID).andExpect(status().isConflict());

    verify(paymentPort, times(1))
        .captureFinalCharge(any(UUID.class), any(UUID.class), any(UUID.class), any());
  }

  @Requirement("KARTY-38")
  @Test
  void calculatesOvertimeAndRollsBackStateWhenPaymentFails() throws Exception {
    startRide("K-101", USER_ID).andExpect(status().isOk());
    var rideId = activeRideId(USER_ID);
    jdbcTemplate.update(
        "update rides set started_at = current_timestamp - interval '61 minutes' where id = ?",
        rideId);
    reportTelemetry("K-101", HUB_ID, 84, false);

    endRide(rideId, USER_ID, HUB_ID)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.finalCharge").value(35.50));

    jdbcTemplate.update("delete from rides");
    jdbcTemplate.update(
        "update karts set status = 'AVAILABLE', locked = true where id = 'K-101'");
    startRide("K-101", USER_ID).andExpect(status().isOk());
    var failedRideId = activeRideId(USER_ID);
    reportTelemetry("K-101", HUB_ID, 84, false);
    when(paymentPort.captureFinalCharge(any(), any(), any(), any())).thenReturn(false);

    endRide(failedRideId, USER_ID, HUB_ID)
        .andExpect(status().isPaymentRequired())
        .andExpect(jsonPath("$.message").value("Final payment failed."));

    assertThat(count("rides where id = '" + failedRideId + "' and status = 'ACTIVE'"))
        .isEqualTo(1);
    assertThat(count("karts where id = 'K-101' and status = 'IN_USE' and locked = false"))
        .isEqualTo(1);
  }

  @Requirement("KARTY-38")
  @Test
  void rejectsMissingAndPreRideTelemetryEvidence() throws Exception {
    startRide("K-101", USER_ID).andExpect(status().isOk());
    var rideId = activeRideId(USER_ID);

    endRide(rideId, USER_ID, HUB_ID)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Park at an authorized hub"));

    var startedAt =
        jdbcTemplate.queryForObject(
            "select started_at from rides where id = ?", Instant.class, rideId);
    reportTelemetry(
        "K-101", HUB_ID, 84, false, startedAt.minusSeconds(1));

    endRide(rideId, USER_ID, HUB_ID)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Park at an authorized hub"));

    verify(paymentPort, times(0))
        .captureFinalCharge(any(UUID.class), any(UUID.class), any(UUID.class), any());
  }

  @Requirement("KARTY-38")
  @Test
  void concurrentReturnsCannotExceedLastHubSpace() throws Exception {
    var secondUserId = UUID.randomUUID();
    userAccountRepository.save(
        new UserAccount(
            secondUserId,
            "karty-38-concurrent-user",
            "karty-38-concurrent-user@example.com",
            "not-used",
            UserRole.END_USER));
    startRide("K-101", USER_ID).andExpect(status().isOk());
    startRide("K-102", secondUserId).andExpect(status().isOk());
    var firstRideId = activeRideId(USER_ID);
    var secondRideId = activeRideId(secondUserId);
    reportTelemetry("K-101", "university-gardens", 80, false);
    reportTelemetry("K-102", "university-gardens", 80, false);
    var occupancy =
        jdbcTemplate.queryForObject(
            "select count(*) from karts where hub_id = 'university-gardens' "
                + "and status <> 'IN_USE'",
            Integer.class);
    jdbcTemplate.update(
        "update hubs set capacity = ? where id = 'university-gardens'", occupancy + 1);
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () -> concurrentEndStatus(firstRideId, USER_ID, ready, start));
      var second =
          executor.submit(
              () -> concurrentEndStatus(secondRideId, secondUserId, ready, start));
      ready.await();
      start.countDown();

      assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 409);
    }

    assertThat(
            count(
                "karts where hub_id = 'university-gardens' and status <> 'IN_USE'"))
        .isEqualTo(occupancy + 1);
    assertThat(count("rides where status = 'COMPLETED'")).isEqualTo(1);
  }

  @Requirement("KARTY-37")
  @Test
  void startsRideOnAvailableKartAndPersistsInUseState() throws Exception {
    startRide("K-101", USER_ID)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.kartId").value("K-101"))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.message").value("Ride started."));

    assertThat(count("rides")).isEqualTo(1);
    assertThat(count("karts where id = 'K-101' and status = 'IN_USE'")).isEqualTo(1);
  }

  @Requirement("KARTY-37")
  @Test
  void acceptsTheTwentyPercentBatteryBoundary() throws Exception {
    startRide("K-102", USER_ID)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    assertThat(count("karts where id = 'K-102' and status = 'IN_USE'")).isEqualTo(1);
  }

  @Requirement("KARTY-37")
  @Test
  void startsRideOnTheUsersReservedKart() throws Exception {
    bookingRepository.save(
        new Booking(
            UUID.randomUUID(),
            USER_ID,
            HUB_ID,
            "K-105",
            Instant.now().minusSeconds(60),
            Instant.now().plusSeconds(1_200),
            BookingStatus.ACTIVE));

    startRide("K-105", USER_ID)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.kartId").value("K-105"));

    assertThat(count("karts where id = 'K-105' and status = 'IN_USE'")).isEqualTo(1);
    assertThat(count("bookings where kart_id = 'K-105' and status = 'COMPLETED'"))
        .isEqualTo(1);
  }

  @Requirement("KARTY-37")
  @Test
  void rejectsReservedRideBeforeItsBookingWindow() throws Exception {
    bookingRepository.save(
        new Booking(
            UUID.randomUUID(),
            USER_ID,
            HUB_ID,
            "K-105",
            Instant.now().plusSeconds(300),
            Instant.now().plusSeconds(1_200),
            BookingStatus.ACTIVE));

    startRide("K-105", USER_ID)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("The kart is no longer available."));

    assertThat(count("rides")).isZero();
    assertThat(count("bookings where kart_id = 'K-105' and status = 'ACTIVE'"))
        .isEqualTo(1);
  }

  @Requirement("KARTY-37")
  @Test
  void immediateRideRequiresDepositAuthorization() throws Exception {
    when(paymentPort.authorizeSecurityDeposit(any(), any())).thenReturn(false);

    startRide("K-101", USER_ID)
        .andExpect(status().isPaymentRequired())
        .andExpect(jsonPath("$.message").value("Security deposit authorization failed."));

    assertThat(count("rides")).isZero();
    assertThat(count("karts where id = 'K-101' and status = 'AVAILABLE'")).isEqualTo(1);
    verify(paymentPort).authorizeSecurityDeposit(any(), any());
  }

  @Requirement("KARTY-37")
  @Test
  void rejectsLowBatteryAndFlagsKartUnavailable() throws Exception {
    startRide("K-103", USER_ID)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Kart has low battery."));

    assertThat(count("rides")).isZero();
    assertThat(count("karts where id = 'K-103' and status = 'LOW_BATTERY'")).isEqualTo(1);
  }

  @Requirement("KARTY-37")
  @Test
  void rejectsASecondActiveRideForTheSameUser() throws Exception {
    startRide("K-101", USER_ID).andExpect(status().isOk());

    startRide("K-102", USER_ID)
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.message").value("Only one active trip is allowed per user."));

    assertThat(count("rides")).isEqualTo(1);
    assertThat(count("karts where id = 'K-102' and status = 'AVAILABLE'")).isEqualTo(1);
  }

  @Requirement("KARTY-37")
  @Test
  void protectsRideEndpointWithAuthenticationAndEndUserRole() throws Exception {
    mockMvc
        .perform(post("/api/rides").content(rideJson("K-101")))
        .andExpect(status().isUnauthorized());

    var operatorId = UUID.randomUUID();
    userAccountRepository.save(
        new UserAccount(
            operatorId,
            "karty-33-operator",
            "karty-33-operator@example.com",
            "not-used",
            UserRole.OPERATOR));

    startRide("K-101", operatorId, "OPERATOR")
        .andExpect(status().isForbidden());
  }

  @Requirement("KARTY-37")
  @Test
  void concurrentRequestsForTheSameUserCreateOnlyOneRide() throws Exception {
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> concurrentRideStatus("K-101", ready, start));
      var second = executor.submit(() -> concurrentRideStatus("K-102", ready, start));
      ready.await();
      start.countDown();

      assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 409);
    }

    assertThat(count("rides where status = 'ACTIVE'")).isEqualTo(1);
  }

  private int concurrentRideStatus(String kartId, CountDownLatch ready, CountDownLatch start)
      throws Exception {
    ready.countDown();
    start.await();
    return startRide(kartId, USER_ID).andReturn().getResponse().getStatus();
  }

  private int concurrentEndStatus(
      UUID rideId, UUID userId, CountDownLatch ready, CountDownLatch start) throws Exception {
    ready.countDown();
    start.await();
    return endRide(rideId, userId, "university-gardens")
        .andReturn()
        .getResponse()
        .getStatus();
  }

  private org.springframework.test.web.servlet.ResultActions startRide(
      String kartId, UUID userId) throws Exception {
    return startRide(kartId, userId, "END_USER");
  }

  private org.springframework.test.web.servlet.ResultActions startRide(
      String kartId, UUID userId, String role) throws Exception {
    return mockMvc
        .perform(
            post("/api/rides")
                .header("Authorization", "Bearer " + token(userId, role))
                .contentType("application/json")
                .content(rideJson(kartId)));
  }

  private org.springframework.test.web.servlet.ResultActions endRide(
      UUID rideId, UUID userId, String hubId) throws Exception {
    return mockMvc
        .perform(
            patch("/api/rides/{rideId}", rideId)
                .header("Authorization", "Bearer " + token(userId, "END_USER"))
                .contentType("application/json")
                .content("{\"hubId\":\"" + hubId + "\"}"));
  }

  private UUID activeRideId(UUID userId) {
    return jdbcTemplate.queryForObject(
        "select id from rides where user_id = ? and status = 'ACTIVE'", UUID.class, userId);
  }

  private void reportTelemetry(
      String kartId, String hubId, int batteryLevel, boolean criticalFault) {
    reportTelemetry(kartId, hubId, batteryLevel, criticalFault, Instant.now());
  }

  private void reportTelemetry(
      String kartId,
      String hubId,
      int batteryLevel,
      boolean criticalFault,
      Instant recordedAt) {
    jdbcTemplate.update(
        "update karts set telemetry_hub_id = ?, battery_level = ?, critical_fault = ?, "
            + "telemetry_recorded_at = ? where id = ?",
        hubId,
        batteryLevel,
        criticalFault,
        Timestamp.from(recordedAt),
        kartId);
  }

  private String rideJson(String kartId) {
    return "{\"hubId\":\"" + HUB_ID + "\",\"kartId\":\"" + kartId + "\"}";
  }

  private String token(UUID userId, String role) {
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

  private int count(String tableExpression) {
    return jdbcTemplate.queryForObject(
        "select count(*) from " + tableExpression, Integer.class);
  }
}
