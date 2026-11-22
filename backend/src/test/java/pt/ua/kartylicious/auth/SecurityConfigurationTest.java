package pt.ua.kartylicious.auth;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import pt.ua.kartylicious.registration.RegistrationController;
import pt.ua.kartylicious.registration.RegistrationService;
import pt.ua.kartylicious.registration.UserAccount;
import pt.ua.kartylicious.registration.UserRole;
import pt.ua.kartylicious.registration.port.UserAccountRepository;

@WebMvcTest({AuthController.class, RegistrationController.class})
@Import({SecurityConfiguration.class, JwtConfiguration.class})
class SecurityConfigurationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtEncoder encoder;
  @MockitoBean private LoginService loginService;
  @MockitoBean private UserAccountRepository userAccountRepository;
  @MockitoBean private RegistrationService registrationService;

  @Requirement("KARTY-34")
  @Test
  void protectedEndpointWithoutTokenIsUnauthorized() throws Exception {
    mockMvc
        .perform(get("/api/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Authentication required"));
  }

  @Requirement("KARTY-34")
  @Test
  void protectedEndpointWithValidTokenIsAccessible() throws Exception {
    var id = UUID.fromString("6a62f8c5-f4a4-4c54-827e-4e31b6ef728e");
    var account =
        new UserAccount(id, "kartfan", "kart@example.com", "hash", UserRole.END_USER);
    org.mockito.Mockito.when(userAccountRepository.findById(id))
        .thenReturn(java.util.Optional.of(account));

    mockMvc
        .perform(get("/api/auth/me").header("Authorization", "Bearer " + validToken(id)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("kartfan"));
  }

  @Requirement("KARTY-34")
  @Test
  void expiredTokenIsUnauthorized() throws Exception {
    var id = UUID.fromString("6a62f8c5-f4a4-4c54-827e-4e31b6ef728e");
    var claims =
        JwtClaimsSet.builder()
            .issuer("kartylicious")
            .subject(id.toString())
            .issuedAt(Instant.now().minusSeconds(120))
            .expiresAt(Instant.now().minusSeconds(60))
            .claim("username", "kartfan")
            .claim("role", "END_USER")
            .build();
    var token = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

    mockMvc
        .perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Authentication required"));
  }

  @Requirement("KARTY-34")
  @Test
  void tamperedTokenIsUnauthorized() throws Exception {
    var id = UUID.fromString("6a62f8c5-f4a4-4c54-827e-4e31b6ef728e");
    var token = validToken(id);
    var tampered = token.substring(0, token.length() - 1) + "x";

    mockMvc
        .perform(get("/api/auth/me").header("Authorization", "Bearer " + tampered))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Authentication required"));
  }

  @Requirement("KARTY-34")
  @Test
  void tokenSignedWithDifferentKeyIsUnauthorized() throws Exception {
    var properties = new JwtProperties();
    properties.setSecret("different-test-secret-not-production-123456");
    properties.setExpirationMinutes(60);
    var otherEncoder = new JwtConfiguration(properties).jwtEncoder();
    var id = UUID.fromString("6a62f8c5-f4a4-4c54-827e-4e31b6ef728e");
    var claims =
        JwtClaimsSet.builder()
            .issuer("kartylicious")
            .subject(id.toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .claim("username", "kartfan")
            .claim("role", "END_USER")
            .build();
    var token = otherEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

    mockMvc
        .perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Authentication required"));
  }

  @Requirement("KARTY-34")
  @Test
  void publicEndpointsRemainReachable() throws Exception {
    mockMvc
        .perform(post("/api/auth/login").contentType("application/json").content(
            """
            {"user":"","password":""}
            """))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(post("/api/registration").contentType("application/json").content(
            """
            {"username":"","email":"","password":""}
            """))
        .andExpect(status().isBadRequest());
  }

  private String validToken(UUID id) {
    var claims =
        JwtClaimsSet.builder()
            .issuer("kartylicious")
            .subject(id.toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .claim("username", "kartfan")
            .claim("role", "END_USER")
            .build();
    return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
  }
}
