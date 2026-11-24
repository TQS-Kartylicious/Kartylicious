package pt.ua.kartylicious.hubs;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import pt.ua.kartylicious.hubs.persistence.HubTestDataResetter;
import pt.ua.kartylicious.registration.UserAccount;
import pt.ua.kartylicious.registration.UserRole;
import pt.ua.kartylicious.registration.port.UserAccountRepository;

@SpringBootTest
@AutoConfigureMockMvc
class HubDirectoryIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private HubTestDataResetter hubTestDataResetter;

    private UUID endUserId;

    @BeforeEach
    void setUp() {
        hubTestDataResetter.resetHubs();
        endUserId = UUID.randomUUID();
        userAccountRepository.save(
                new UserAccount(
                        endUserId,
                        "karty-35-end-user-" + endUserId,
                        "karty-35-end-user-" + endUserId + "@example.com",
                        "not-used",
                        UserRole.END_USER));
    }

    @Requirement("KARTY-35")
    @Test
    void endUserCanBrowseHubsAndSeeKartAvailabilityAndBatteryLevels() throws Exception {
        var authorization = "Bearer " + endUserToken();

        mockMvc
                .perform(get("/api/hubs"))
                .andExpect(status().isUnauthorized());

        mockMvc
                .perform(get("/api/hubs").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("central-square"))
                .andExpect(jsonPath("$[0].availableKartCount").value(2))
                .andExpect(jsonPath("$[1].id").value("riverside-park"))
                .andExpect(jsonPath("$[1].availableKartCount").value(0));

        mockMvc
                .perform(get("/api/hubs/central-square").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.karts.length()").value(6))
                .andExpect(jsonPath("$.karts[0].batteryLevel").value(84))
                .andExpect(jsonPath("$.karts[0].status").value("Available"))
                .andExpect(jsonPath("$.karts[0].selectable").value(true))
                .andExpect(jsonPath("$.karts[1].batteryLevel").value(20))
                .andExpect(jsonPath("$.karts[1].status").value("Available"))
                .andExpect(jsonPath("$.karts[1].selectable").value(true))
                .andExpect(jsonPath("$.karts[2].batteryLevel").value(19))
                .andExpect(jsonPath("$.karts[2].status").value("Low Battery"))
                .andExpect(jsonPath("$.karts[2].selectable").value(false))
                .andExpect(jsonPath("$.karts[3].status").value("In Use"))
                .andExpect(jsonPath("$.karts[3].selectable").value(false))
                .andExpect(jsonPath("$.karts[4].status").value("Reserved"))
                .andExpect(jsonPath("$.karts[4].selectable").value(false))
                .andExpect(jsonPath("$.karts[5].status").value("In Maintenance"))
                .andExpect(jsonPath("$.karts[5].selectable").value(false));

        mockMvc
                .perform(get("/api/hubs/riverside-park").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.karts.length()").value(2))
                .andExpect(jsonPath("$.karts[0].selectable").value(false))
                .andExpect(jsonPath("$.karts[1].selectable").value(false));
    }

    private String endUserToken() {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("kartylicious-test")
                .subject(endUserId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("role", "END_USER")
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
