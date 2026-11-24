package pt.ua.kartylicious.rides;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import pt.ua.kartylicious.auth.JwtConfiguration;
import pt.ua.kartylicious.auth.SecurityConfiguration;
import pt.ua.kartylicious.registration.UserAccount;
import pt.ua.kartylicious.registration.UserRole;
import pt.ua.kartylicious.registration.port.UserAccountRepository;

@WebMvcTest(RideController.class)
@Import({SecurityConfiguration.class, JwtConfiguration.class})
class RideControllerTest {

  private static final UUID USER_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000033");
  private static final UUID RIDE_ID =
      UUID.fromString("10000000-0000-0000-0000-000000000033");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtEncoder jwtEncoder;

  @MockitoBean private RideService rideService;
  @MockitoBean private UserAccountRepository userAccountRepository;

  @Test
  @Requirement("KARTY-37")
  void endUserCanStartAnAvailableRide() throws Exception {
    var startedAt = Instant.parse("2099-01-01T10:00:00Z");
    when(rideService.start(any(RideRequest.class), eq(USER_ID)))
        .thenReturn(
            new RideResponse(
                RIDE_ID, "hub-1", "kart-1", startedAt, "ACTIVE", "Ride started."));

    mockMvc
        .perform(
            post("/api/rides")
                .header("Authorization", "Bearer " + tokenForRole("END_USER"))
                .contentType("application/json")
                .content("{\"hubId\":\"hub-1\",\"kartId\":\"kart-1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(RIDE_ID.toString()))
        .andExpect(jsonPath("$.hubId").value("hub-1"))
        .andExpect(jsonPath("$.kartId").value("kart-1"))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.message").value("Ride started."));

    verify(rideService).start(new RideRequest("hub-1", "kart-1"), USER_ID);
  }

  @Test
  @Requirement("KARTY-37")
  void endUserCanStartTheirReservedRide() throws Exception {
    when(rideService.start(any(RideRequest.class), eq(USER_ID)))
        .thenReturn(
            new RideResponse(
                RIDE_ID,
                "hub-1",
                "reserved-kart",
                Instant.parse("2099-01-01T10:00:00Z"),
                "ACTIVE",
                "Ride started."));

    mockMvc
        .perform(
            post("/api/rides")
                .header("Authorization", "Bearer " + tokenForRole("END_USER"))
                .contentType("application/json")
                .content("{\"hubId\":\"hub-1\",\"kartId\":\"reserved-kart\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.kartId").value("reserved-kart"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  @Requirement("KARTY-37")
  void returnsLowBatteryMessageWhenRideCannotStart() throws Exception {
    when(rideService.start(any(RideRequest.class), eq(USER_ID)))
        .thenThrow(new LowBatteryException());

    mockMvc
        .perform(
            post("/api/rides")
                .header("Authorization", "Bearer " + tokenForRole("END_USER"))
                .contentType("application/json")
                .content(rideJson()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Kart has low battery."));
  }

  @Test
  @Requirement("KARTY-37")
  void returnsActiveTripMessageWhenUserStartsASecondRide() throws Exception {
    when(rideService.start(any(RideRequest.class), eq(USER_ID)))
        .thenThrow(new ActiveRideException());

    mockMvc
        .perform(
            post("/api/rides")
                .header("Authorization", "Bearer " + tokenForRole("END_USER"))
                .contentType("application/json")
                .content(rideJson()))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.message").value("Only one active trip is allowed per user."));
  }

  @Test
  @Requirement("KARTY-37")
  void rideEndpointRequiresAnAuthenticatedEndUser() throws Exception {
    mockMvc
        .perform(post("/api/rides").contentType("application/json").content(rideJson()))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post("/api/rides")
                .header("Authorization", "Bearer " + tokenForRole("OPERATOR"))
                .contentType("application/json")
                .content(rideJson()))
        .andExpect(status().isForbidden());
  }

  private String rideJson() {
    return "{\"hubId\":\"hub-1\",\"kartId\":\"kart-1\"}";
  }

  private String tokenForRole(String role) {
    var now = Instant.now();
    when(userAccountRepository.findById(USER_ID))
        .thenReturn(
            Optional.of(
                new UserAccount(
                    USER_ID,
                    "karty-33-test-user",
                    "karty-33-test-user@example.com",
                    "not-used",
                    UserRole.valueOf(role))));
    var claims =
        JwtClaimsSet.builder()
            .issuer("kartylicious-test")
            .subject(USER_ID.toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .claim("role", role)
            .build();
    return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
  }
}
