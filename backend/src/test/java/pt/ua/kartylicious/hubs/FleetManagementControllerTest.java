package pt.ua.kartylicious.hubs;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pt.ua.kartylicious.auth.JwtConfiguration;
import pt.ua.kartylicious.auth.SecurityConfiguration;
import pt.ua.kartylicious.bookings.port.BookingRepository;
import pt.ua.kartylicious.hubs.port.HubRepository;
import pt.ua.kartylicious.registration.UserAccount;
import pt.ua.kartylicious.registration.UserRole;
import pt.ua.kartylicious.registration.port.UserAccountRepository;

@WebMvcTest(FleetManagementController.class)
@Import({SecurityConfiguration.class, JwtConfiguration.class, FleetManagementService.class})
class FleetManagementControllerTest {
  private static final String HUBS = "/api/operator/hubs";
  private static final String KARTS = "/api/operator/karts";
  private static final String VALID_HUB = "{\"name\":\"New hub\",\"address\":\"Location\",\"capacity\":1}";
  private static final String VALID_KART = "{\"id\":\"K-new\",\"hubId\":\"hub\"}";

  @Autowired private MockMvc mvc;
  @Autowired private JwtEncoder encoder;
  @MockitoBean private Clock clock;
  @MockitoBean private UserAccountRepository users;
  @MockitoBean private HubRepository hubs;
  @MockitoBean private BookingRepository bookings;

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"END_USER", "MUNICIPAL_WORKER", "ADMIN"})
  @Requirement({"KARTY-48", "KARTY-49", "KARTY-50"})
  void everyFleetRouteRequiresOperatorRole(String role) throws Exception {
    var authorization = role == null ? null : token(UserRole.valueOf(role));
    for (var request : List.of(get(HUBS), post(HUBS).content(VALID_HUB),
        put(HUBS + "/hub").content(VALID_HUB), post(KARTS).content(VALID_KART),
        patch(KARTS + "/K-new").content("{\"active\":true}"))) {
      if (authorization != null) {
        request.header("Authorization", authorization);
      }
      mvc.perform(request.contentType("application/json"))
          .andExpect(status().is(role == null ? 401 : 403));
    }
    verifyNoInteractions(hubs, bookings);
  }

  @ParameterizedTest
  @ValueSource(strings = {"-1", "0", "1.5", "\"2\"", "2147483648"})
  @Requirement("KARTY-48")
  void rejectsInvalidOrNonIntegralCapacity(String capacity) throws Exception {
    mvc.perform(post(HUBS).header("Authorization", token(UserRole.OPERATOR))
        .contentType("application/json").content(VALID_HUB.replace(":1", ":" + capacity)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Hub capacity must be a positive integer."));
  }

  @Test
  @Requirement("KARTY-48")
  void createsAndUpdatesAHubsDetailsKeepingItsIdentifier() throws Exception {
    var authorization = token(UserRole.OPERATOR);
    var original = new Hub("hub", "Old hub", "Old location", 2, List.of());
    when(hubs.findAll()).thenReturn(List.of(original));
    when(hubs.lockHub("hub")).thenReturn(Optional.of(original));
    mvc.perform(get(HUBS).header("Authorization", authorization))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].occupancy").value(0));
    mvc.perform(post(HUBS).header("Authorization", authorization)
        .contentType("application/json").content(VALID_HUB))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isString())
        .andExpect(jsonPath("$.message").value("Hub registered."));
    mvc.perform(put(HUBS + "/hub").header("Authorization", authorization)
        .contentType("application/json").content(VALID_HUB))
        .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("hub"))
        .andExpect(jsonPath("$.address").value("Location"))
        .andExpect(jsonPath("$.message").value("Hub updated."));
  }

  @Test
  @Requirement("KARTY-49")
  void reportsMissingFullAndDuplicateCasesAndReturnsInactiveOnSuccess() throws Exception {
    var authorization = token(UserRole.OPERATOR);
    when(hubs.lockHub("hub")).thenReturn(Optional.of(new Hub("hub", "Hub", "Address", 1, List.of())));
    when(hubs.lockHub("full")).thenReturn(Optional.of(new Hub("full", "Full", "Address", 1, List.of(
        new Kart("parked", "Parked", 0, KartStatus.INACTIVE, false, false)))));
    when(hubs.kartExists("duplicate")).thenReturn(true);
    mvc.perform(post(KARTS).header("Authorization", authorization)
        .contentType("application/json").content(VALID_KART))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.kart.active").value(false))
        .andExpect(jsonPath("$.kart.status").value("INACTIVE"))
        .andExpect(jsonPath("$.message").value("Mini-kart registered."));
    var cases = List.of(
        new KartError("missing", "K-new", 404, "Invalid hub."),
        new KartError("full", "K-new", 409, "The hub is full."),
        new KartError("hub", "duplicate", 409, "A mini-kart with this identifier already exists."));
    for (var test : cases) {
      var body = VALID_KART.replace("K-new", test.id()).replace("\"hub\"", "\"" + test.hub() + "\"");
      mvc.perform(post(KARTS).header("Authorization", authorization)
          .contentType("application/json").content(body))
          .andExpect(status().is(test.status()))
          .andExpect(jsonPath("$.message").value(test.message()));
    }
  }

  @Test
  @Requirement("KARTY-50")
  void reportsRetiredConflictAndSuccessfulActivation() throws Exception {
    var authorization = token(UserRole.OPERATOR);
    when(hubs.lockKartById("retired"))
        .thenReturn(Optional.of(new Kart("retired", "Retired", 20, KartStatus.RETIRED, false, false)));
    when(hubs.lockKartById("ready"))
        .thenReturn(Optional.of(new Kart("ready", "Ready", 20, KartStatus.INACTIVE, false, false)));
    when(clock.instant()).thenReturn(Instant.now());
    mvc.perform(patch(KARTS + "/retired").header("Authorization", authorization)
        .contentType("application/json").content("{\"active\":false}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Retired mini-karts cannot be reactivated."));
    mvc.perform(patch(KARTS + "/ready").header("Authorization", authorization)
        .contentType("application/json").content("{\"active\":true}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.kart.status").value("AVAILABLE"))
        .andExpect(jsonPath("$.message").value("Mini-kart activated."));
  }

  private String token(UserRole role) {
    var id = UUID.randomUUID();
    var now = Instant.now();
    when(users.findById(id)).thenReturn(Optional.of(
        new UserAccount(id, "fleet-user", "fleet@example.test", "unused", role)));
    return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwtClaimsSet.builder()
        .subject(id.toString()).issuedAt(now).expiresAt(now.plusSeconds(300))
        .claim("role", role.name()).build())).getTokenValue();
  }

  private record KartError(String hub, String id, int status, String message) {}
}
