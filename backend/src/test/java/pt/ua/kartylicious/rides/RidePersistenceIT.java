package pt.ua.kartylicious.rides;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import pt.ua.kartylicious.bookings.Booking;
import pt.ua.kartylicious.bookings.BookingStatus;
import pt.ua.kartylicious.bookings.port.BookingRepository;
import pt.ua.kartylicious.registration.UserAccount;
import pt.ua.kartylicious.registration.UserRole;
import pt.ua.kartylicious.registration.port.UserAccountRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("Layer1")
class RidePersistenceIT {

  private static final String HUB_ID = "central-square";
  private static final UUID USER_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000033");

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private BookingRepository bookingRepository;
  @Autowired private UserAccountRepository userAccountRepository;

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
  }

  @Requirement("KARTY-33")
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

  @Requirement("KARTY-33")
  @Test
  void acceptsTheTwentyPercentBatteryBoundary() throws Exception {
    startRide("K-102", USER_ID)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    assertThat(count("karts where id = 'K-102' and status = 'IN_USE'")).isEqualTo(1);
  }

  @Requirement("KARTY-33")
  @Test
  void startsRideOnTheUsersReservedKart() throws Exception {
    bookingRepository.save(
        new Booking(
            UUID.randomUUID(),
            USER_ID,
            HUB_ID,
            "K-105",
            Instant.now().plusSeconds(60),
            Instant.now().plusSeconds(1_200),
            BookingStatus.ACTIVE));

    startRide("K-105", USER_ID)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.kartId").value("K-105"));

    assertThat(count("karts where id = 'K-105' and status = 'IN_USE'")).isEqualTo(1);
  }

  @Requirement("KARTY-33")
  @Test
  void rejectsLowBatteryAndFlagsKartUnavailable() throws Exception {
    startRide("K-103", USER_ID)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Kart has low battery."));

    assertThat(count("rides")).isZero();
    assertThat(count("karts where id = 'K-103' and status = 'LOW_BATTERY'")).isEqualTo(1);
  }

  @Requirement("KARTY-33")
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

  @Requirement("KARTY-33")
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
