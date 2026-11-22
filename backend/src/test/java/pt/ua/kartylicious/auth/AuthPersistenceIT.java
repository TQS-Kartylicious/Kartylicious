package pt.ua.kartylicious.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthPersistenceIT {

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16-alpine");

  @Autowired private MockMvc mockMvc;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
  }

  @Requirement("KARTY-34")
  @Test
  void registerLoginAndMeUseTheSamePersistedAccount() throws Exception {
    mockMvc
        .perform(
            post("/api/registration")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"username":"authfan","email":"auth@example.com","password":"strong-pass-123"}
                    """))
        .andExpect(status().isCreated());

    var loginResult =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"user":" AUTH@Example.com ","password":"strong-pass-123"}
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isString())
            .andExpect(jsonPath("$.user.email").value("auth@example.com"))
            .andReturn();

    var token =
        com.jayway.jsonpath.JsonPath.read(
            loginResult.getResponse().getContentAsString(), "$.token");

    mockMvc
        .perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("authfan"))
        .andExpect(jsonPath("$.email").value("auth@example.com"))
        .andExpect(jsonPath("$.role").value("END_USER"));
  }
}
