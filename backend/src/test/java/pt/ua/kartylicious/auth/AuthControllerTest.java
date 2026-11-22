package pt.ua.kartylicious.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pt.ua.kartylicious.registration.UserRole;
import pt.ua.kartylicious.registration.port.UserAccountRepository;

@WebMvcTest(AuthController.class)
@Import({SecurityConfiguration.class, JwtConfiguration.class})
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private LoginService loginService;
  @MockitoBean private UserAccountRepository userAccountRepository;

  @Requirement("KARTY-34")
  @Test
  void loginReturnsTokenAndUser() throws Exception {
    var user =
        new LoginResponse.UserResponse(
            UUID.fromString("6a62f8c5-f4a4-4c54-827e-4e31b6ef728e"),
            "kartfan",
            "kart@example.com",
            UserRole.END_USER);
    when(loginService.login(anyString(), anyString()))
        .thenReturn(new LoginResponse("signed-token", "Bearer", 3600, user));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content(
                    """
                    {"user":"kart@example.com","password":"strong-pass-123"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value("signed-token"))
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.expiresIn").value(3600))
        .andExpect(jsonPath("$.password").doesNotExist())
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
  }

  @Requirement("KARTY-34")
  @Test
  void loginRejectsBlankFieldsWithFieldErrors() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content(
                    """
                    {"user":"","password":""}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.email").value("The email is required"))
        .andExpect(jsonPath("$.password").value("The password is required"));
  }

  @Requirement("KARTY-34")
  @Test
  void loginReturnsExactSameUnauthorizedMessageForInvalidCredentials() throws Exception {
    when(loginService.login(anyString(), anyString()))
        .thenThrow(new InvalidCredentialsException());

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content(
                    """
                    {"user":"unknown@example.com","password":"wrong-password"}
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Incorrect email or password."));
  }
}
