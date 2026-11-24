package pt.ua.kartylicious.bookings;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import pt.ua.kartylicious.bookings.port.PaymentPort;
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

  @MockitoBean private PaymentPort paymentPort;

  @DynamicPropertySource
  static void registerDatabaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
  }

  @BeforeEach
  void resetBookingState() {
    jdbcTemplate.update("delete from bookings");
    jdbcTemplate.update("update karts set status = 'AVAILABLE' where id in ('K-101', 'K-102')");
    userAccountRepository.save(
        new UserAccount(
            FIRST_USER_ID,
            "karty-32-first-user",
            "karty-32-first-user@example.com",
            "not-used",
            UserRole.END_USER));
    when(paymentPort.authorizeSecurityDeposit(any(), any())).thenReturn(true);
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
    when(paymentPort.authorizeSecurityDeposit(any(), any())).thenReturn(false);

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
