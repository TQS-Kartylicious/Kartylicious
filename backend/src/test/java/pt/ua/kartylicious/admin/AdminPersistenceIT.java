package pt.ua.kartylicious.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminPersistenceIT {

  private static final String ADMIN_EMAIL = "seed-admin@example.com";
  private static final String ADMIN_PASSWORD = "strong-admin-pass-123";
  private static final String STAFF_PASSWORD = "strong-pass-123";

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16-alpine");

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String adminToken;

  @DynamicPropertySource
  static void registerDatabaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    registry.add("jwt.secret", () -> "01234567890123456789012345678901");
    registry.add("admin.username", () -> "seed-admin");
    registry.add("admin.email", () -> ADMIN_EMAIL);
    registry.add("admin.password", () -> ADMIN_PASSWORD);
  }

  @BeforeEach
  void loginAsSeededAdmin() throws Exception {
    adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
  }

  @AfterEach
  void removeTestAccounts() {
    jdbcTemplate.update("delete from rides");
    jdbcTemplate.update("delete from bookings");
    jdbcTemplate.update("delete from user_accounts where email <> ?", ADMIN_EMAIL);
  }

  @Requirement("KARTY-76")
  @Test
  void seededAdminCanListUsersAndCreateStaff() throws Exception {
    mockMvc
        .perform(get("/api/admin/users").header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString(ADMIN_EMAIL)));

    var username = "operator-" + uniqueSuffix();
    var email = username + "@example.com";
    var userId = createStaff(username, email, "OPERATOR");

    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from user_accounts where id = ? and role = 'OPERATOR' and active",
                Integer.class,
                UUID.fromString(userId)))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "select password_hash from user_accounts where email = ?",
                String.class,
                email))
        .isNotEqualTo(STAFF_PASSWORD);
  }

  @Requirement("KARTY-76")
  @Test
  void adminCanChangeRoleAndDeactivateStaff() throws Exception {
    var username = "operator-" + uniqueSuffix();
    var email = username + "@example.com";
    var userId = createStaff(username, email, "OPERATOR");
    var operatorToken = login(email, STAFF_PASSWORD);

    mockMvc
        .perform(
            patch("/api/admin/users/{id}/role", userId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"MUNICIPAL_WORKER\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.role").value("MUNICIPAL_WORKER"));

    mockMvc
        .perform(
            patch("/api/admin/users/{id}/status", userId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.active").value(false));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(email, STAFF_PASSWORD)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("This account is deactivated."));

    mockMvc
        .perform(get("/api/admin/users").header("Authorization", bearer(operatorToken)))
        .andExpect(status().isUnauthorized());

    assertThat(
            jdbcTemplate.queryForObject(
                "select active from user_accounts where email = ?",
                Boolean.class,
                email))
        .isFalse();
  }


  @Requirement("KARTY-76")
  @Test
  void invalidRoleAndUnsupportedStatusAreRejected() throws Exception {
    var username = "operator-" + uniqueSuffix();
    var userId = createStaff(username, username + "@example.com", "OPERATOR");

    mockMvc
        .perform(
            patch("/api/admin/users/{id}/role", userId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"unknown\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("The role is invalid."));

    mockMvc
        .perform(
            patch("/api/admin/users/{id}/status", userId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":true}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Only active=false is supported."));
  }

  @Requirement("KARTY-76")
  @Test
  void lastActiveAdminCannotBeDeactivated() throws Exception {
    var adminId =
        jdbcTemplate.queryForObject(
            "select id from user_accounts where email = ?",
            UUID.class,
            ADMIN_EMAIL);

    mockMvc
        .perform(
            patch("/api/admin/users/{id}/status", adminId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("At least one active Admin is required."));
  }

  @Requirement("KARTY-76")
  @Test
  void nonAdminIsForbiddenAndAnonymousUserIsUnauthorized() throws Exception {
    var username = "ordinary-" + uniqueSuffix();
    var email = username + "@example.com";
    mockMvc
        .perform(
            post("/api/registration")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"username":"%s","email":"%s","password":"%s"}
                    """
                        .formatted(username, email, STAFF_PASSWORD)))
        .andExpect(status().isCreated());

    var ordinaryToken = login(email, STAFF_PASSWORD);
    mockMvc
        .perform(get("/api/admin/users").header("Authorization", bearer(ordinaryToken)))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(get("/api/admin/users"))
        .andExpect(status().isUnauthorized());
  }

  @Requirement("KARTY-76")
  @Test
  void adminCanBeDemotedWhenAnotherAdminRemains() throws Exception {
    var username = "admin-" + uniqueSuffix();
    var newAdminId = createStaff(username, username + "@example.com", "ADMIN");

    mockMvc
        .perform(
            patch("/api/admin/users/{id}/role", newAdminId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"OPERATOR\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.role").value("OPERATOR"));

    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from user_accounts where role = 'ADMIN' and active",
                Integer.class))
        .isEqualTo(1);
  }

  @Requirement("KARTY-76")
  @Test
  void userWithActiveReservationOrRideCannotBeDeactivated() throws Exception {
    var suffix = uniqueSuffix();
    var userId = createEndUser("rider-" + suffix, "rider-" + suffix + "@example.com");
    var now = Instant.now();
    jdbcTemplate.update(
        "insert into bookings (id, user_id, hub_id, kart_id, starts_at, ends_at, status) "
            + "values (?, ?, ?, ?, ?, ?, 'ACTIVE')",
        UUID.randomUUID(),
        userId,
        "central-square",
        "K-101",
        Timestamp.from(now.plusSeconds(60)),
        Timestamp.from(now.plusSeconds(1_200)));

    deactivate(userId).andExpect(status().isConflict());

    jdbcTemplate.update("delete from bookings where user_id = ?", userId);
    jdbcTemplate.update(
        "insert into rides (id, user_id, hub_id, kart_id, started_at, status) "
            + "values (?, ?, ?, ?, ?, 'ACTIVE')",
        UUID.randomUUID(),
        userId,
        "central-square",
        "K-101",
        Timestamp.from(now));

    deactivate(userId).andExpect(status().isConflict());
  }

  private org.springframework.test.web.servlet.ResultActions deactivate(UUID userId)
      throws Exception {
    return mockMvc.perform(
        patch("/api/admin/users/{id}/status", userId)
            .header("Authorization", bearer(adminToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"active\":false}"));
  }

  private UUID createEndUser(String username, String email) throws Exception {
    var response =
        mockMvc
            .perform(
                post("/api/registration")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"username":"%s","email":"%s","password":"%s"}
                        """
                            .formatted(username, email, STAFF_PASSWORD)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(
        JsonPath.read(response.getResponse().getContentAsString(), "$.id").toString());
  }

  private String createStaff(String username, String email, String role) throws Exception {
    var response =
        mockMvc
            .perform(
                post("/api/admin/users")
                    .header("Authorization", bearer(adminToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(staffJson(username, email, role)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(response.getResponse().getContentAsString(), "$.user.id").toString();
  }

  private String login(String email, String password) throws Exception {
    var response =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginJson(email, password)))
            .andExpect(status().isOk())
            .andReturn();
    return JsonPath.read(response.getResponse().getContentAsString(), "$.token").toString();
  }

  private String staffJson(String username, String email, String role) {
    return """
        {"username":"%s","email":"%s","password":"%s","role":"%s"}
        """
        .formatted(username, email, STAFF_PASSWORD, role);
  }

  private String loginJson(String email, String password) {
    return """
        {"user":"%s","password":"%s"}
        """
        .formatted(email, password);
  }

  private String uniqueSuffix() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }
}
