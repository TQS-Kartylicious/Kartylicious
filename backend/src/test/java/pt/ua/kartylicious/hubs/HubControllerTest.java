package pt.ua.kartylicious.hubs;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import pt.ua.kartylicious.auth.JwtConfiguration;
import pt.ua.kartylicious.auth.SecurityConfiguration;
import pt.ua.kartylicious.hubs.HubDirectoryService.HubDetails;
import pt.ua.kartylicious.hubs.HubDirectoryService.HubSummary;
import pt.ua.kartylicious.hubs.HubDirectoryService.KartDetails;

@WebMvcTest(HubController.class)
@Import({SecurityConfiguration.class, JwtConfiguration.class})
class HubControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtEncoder jwtEncoder;

  @MockitoBean private HubDirectoryService hubDirectoryService;

  @Requirement("KARTY-35")
  @Test
  void endUserCanBrowseHubsAndSeeKartBatteryAvailabilityAndSelectableStatus() throws Exception {
    when(hubDirectoryService.listHubs())
        .thenReturn(List.of(new HubSummary("hub-1", "Riverside Hub", "1 River Road", 1)));
    when(hubDirectoryService.getHub("hub-1"))
        .thenReturn(
            new HubDetails(
                "hub-1",
                "Riverside Hub",
                "1 River Road",
                List.of(
                    new KartDetails("kart-1", "Blue kart", 20, "Available", true),
                    new KartDetails("kart-2", "Yellow kart", 19, "Available", false),
                    new KartDetails("kart-3", "Green kart", 80, "In Use", false),
                    new KartDetails("kart-4", "Red kart", 80, "Reserved", false),
                    new KartDetails("kart-5", "White kart", 80, "In Maintenance", false),
                    new KartDetails("kart-6", "Orange kart", 80, "Available", false))));

    var authorization = "Bearer " + tokenForRole("END_USER");

    mockMvc
        .perform(get("/api/hubs").header("Authorization", authorization))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("hub-1"))
        .andExpect(jsonPath("$[0].availableKartCount").value(1));

    mockMvc
        .perform(get("/api/hubs/hub-1").header("Authorization", authorization))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("hub-1"))
        .andExpect(jsonPath("$.name").value("Riverside Hub"))
        .andExpect(jsonPath("$.karts.length()").value(6))
        .andExpect(jsonPath("$.karts[0].batteryLevel").value(20))
        .andExpect(jsonPath("$.karts[0].status").value("Available"))
        .andExpect(jsonPath("$.karts[0].selectable").value(true))
        .andExpect(jsonPath("$.karts[1].batteryLevel").value(19))
        .andExpect(jsonPath("$.karts[1].selectable").value(false))
        .andExpect(jsonPath("$.karts[2].status").value("In Use"))
        .andExpect(jsonPath("$.karts[2].selectable").value(false))
        .andExpect(jsonPath("$.karts[3].status").value("Reserved"))
        .andExpect(jsonPath("$.karts[3].selectable").value(false))
        .andExpect(jsonPath("$.karts[4].status").value("In Maintenance"))
        .andExpect(jsonPath("$.karts[4].selectable").value(false))
        .andExpect(jsonPath("$.karts[5].selectable").value(false));
  }

  @Requirement("KARTY-35")
  @Test
  void hubWithNoAvailableKartsReturnsAllKartsAsNotSelectable() throws Exception {
    when(hubDirectoryService.listHubs())
        .thenReturn(List.of(new HubSummary("empty-hub", "Quiet Hub", "2 Park Road", 0)));
    when(hubDirectoryService.getHub("empty-hub"))
        .thenReturn(
            new HubDetails(
                "empty-hub",
                "Quiet Hub",
                "2 Park Road",
                List.of(
                    new KartDetails("kart-7", "Gray kart", 19, "Available", false),
                    new KartDetails("kart-8", "Black kart", 80, "In Use", false))));

    var authorization = "Bearer " + tokenForRole("END_USER");

    mockMvc
        .perform(get("/api/hubs").header("Authorization", authorization))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].availableKartCount").value(0));

    mockMvc
        .perform(get("/api/hubs/empty-hub").header("Authorization", authorization))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.karts.length()").value(2))
        .andExpect(jsonPath("$.karts[0].selectable").value(false))
        .andExpect(jsonPath("$.karts[1].selectable").value(false));
  }

  @Requirement("KARTY-35")
  @Test
  void hubDirectoryRequiresAnAuthenticatedEndUser() throws Exception {
    mockMvc.perform(get("/api/hubs/hub-1")).andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            get("/api/hubs/hub-1")
                .header("Authorization", "Bearer " + tokenForRole("OPERATOR")))
        .andExpect(status().isForbidden());
  }

  private String tokenForRole(String role) {
    var now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .issuer("kartylicious-test")
            .subject("karty-35-test-user")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .claim("role", role)
            .build();
    return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
  }
}
