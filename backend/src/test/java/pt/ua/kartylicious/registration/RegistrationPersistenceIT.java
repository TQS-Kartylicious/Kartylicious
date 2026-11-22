package pt.ua.kartylicious.registration;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RegistrationPersistenceIT {

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16-alpine");

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @DynamicPropertySource
  static void registerDatabaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
  }

  @Requirement("KARTY-27")
  @Test
  void registrationPersistsEndUserAndRejectsDuplicateEmailRegardlessOfCase() throws Exception {
    mockMvc
        .perform(
            post("/api/registration")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"username":"kartfan","email":"kart@example.com","password":"strong-pass-123"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.role").value("END_USER"));

    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from user_accounts where email = ?",
                Integer.class,
                "kart@example.com"))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "select password_hash from user_accounts where email = ?",
                String.class,
                "kart@example.com"))
        .isNotEqualTo("strong-pass-123");

    mockMvc
        .perform(
            post("/api/registration")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"username":"anotherfan","email":"KART@example.com","password":"strong-pass-123"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("The email is already in use"));
  }
}
