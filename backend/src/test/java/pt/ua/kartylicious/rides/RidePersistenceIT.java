package pt.ua.kartylicious.rides;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pt.ua.kartylicious.bookings.Booking;
import pt.ua.kartylicious.bookings.BookingStatus;
import pt.ua.kartylicious.bookings.port.BookingRepository;
import pt.ua.kartylicious.bookings.port.PaymentPort;
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
  @MockitoBean private PaymentPort paymentPort;

  @DynamicPropertySource
  static void registerDatabaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
  }

  @BeforeEach
  void resetRideState() {
    jdbcTemplate.update("delete from rides");
    jdbcTemplate.update("delete from bookings");
    jdbcTemplate.update(
        "update karts set status = case id "
            + "when 'K-103' then 'LOW_BATTERY' "
            + "when 'K-104' then 'IN_USE' "
            + "when 'K-105' then 'RESERVED' "
            + "when 'K-106' then 'IN_MAINTENANCE' "
            + "else 'AVAILABLE' end");
    userAccountRepository.save(
        new UserAccount(
            USER_ID,
            "karty-33-end-user",
            "karty-33-end-user@example.com",
            "not-used",
            UserRole.END_USER));
    when(paymentPort.authorizeSecurityDeposit(any(), any())).thenReturn(true);
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
