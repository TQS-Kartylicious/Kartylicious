package pt.ua.kartylicious.registration;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RegistrationController.class)
class RegistrationControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private RegistrationService registrationService;

  @Requirement("KARTY-27")
  @Test
  void registerReturnsCreatedEndUserWithExpectedFields() throws Exception {
    when(registrationService.register(anyString(), anyString(), anyString()))
        .thenReturn(
            new UserAccount(
                UUID.fromString("6a62f8c5-f4a4-4c54-827e-4e31b6ef728e"),
                "kartfan",
                "kart@example.com",
                "hashed-password",
                UserRole.END_USER));

    mockMvc
        .perform(
            post("/api/registration")
                .contentType("application/json")
                .content(
                    """
                    {"username":"kartfan","email":"kart@example.com","password":"strong-pass-123"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.username").value("kartfan"))
        .andExpect(jsonPath("$.email").value("kart@example.com"))
        .andExpect(jsonPath("$.role").value("END_USER"))
        .andExpect(jsonPath("$.password").doesNotExist())
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
  }

  @Requirement("KARTY-27")
  @Test
  void registerRejectsInvalidEmailWithErrorMessage() throws Exception {
    mockMvc
        .perform(
            post("/api/registration")
                .contentType("application/json")
                .content(
                    """
                    {"username":"kartfan","email":"not-an-email","password":"strong-pass-123"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.email").value("The email format is incorrect"));

        verifyNoInteractions(registrationService);
  }

  @Requirement("KARTY-27")
  @Test
  void registerRejectsShortPasswordWithErrorMessage() throws Exception {
    mockMvc
        .perform(
            post("/api/registration")
                .contentType("application/json")
                .content(
                    """
                    {"username":"kartfan","email":"kart@example.com","password":"short"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.password")
                .value("The password must be equal or longer than 8 characters"));

        verifyNoInteractions(registrationService);
  }

  @Requirement("KARTY-27")
  @Test
  void registerRejectsEmptyRequestBodyFieldsRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/registration")
                .contentType("application/json")
                .content(
                    """
                    {"username":"","email":"kart@example.com","password":"short"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.username").value("The username is required"))
        .andExpect(
            jsonPath("$.password")
                .value("The password must be equal or longer than 8 characters"));

    verifyNoInteractions(registrationService);
  }

  @Requirement("KARTY-27")
  @Test
  void registerReportsDuplicateEmail() throws Exception {
    when(registrationService.register("kartfan", "kart@example.com", "strong-pass-123"))
        .thenThrow(new EmailAlreadyInUseException());

    mockMvc
        .perform(
            post("/api/registration")
                .contentType("application/json")
                .content(
                    """
                    {"username":"kartfan","email":"kart@example.com","password":"strong-pass-123"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("The email is already in use"));
  }
}
