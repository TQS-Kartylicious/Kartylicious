package pt.ua.kartylicious.bookings;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
import pt.ua.kartylicious.auth.JwtConfiguration;
import pt.ua.kartylicious.auth.SecurityConfiguration;
import pt.ua.kartylicious.registration.UserAccount;
import pt.ua.kartylicious.registration.UserRole;
import pt.ua.kartylicious.registration.port.UserAccountRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
@Import({SecurityConfiguration.class, JwtConfiguration.class})
class BookingControllerTest {

  private static final UUID USER_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000036");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtEncoder jwtEncoder;

  @MockitoBean private BookingService bookingService;
  @MockitoBean private UserAccountRepository userAccountRepository;

  @Requirement("KARTY-36")
  @Test
  void endUserCanBookAKartAndReceivesConfirmation() throws Exception {
    var bookingId = UUID.fromString("10000000-0000-0000-0000-000000000036");
    var startsAt = Instant.parse("2099-01-01T10:00:00Z");
    var endsAt = Instant.parse("2099-01-01T10:20:00Z");
    when(bookingService.book(any(BookingRequest.class), eq(USER_ID)))
        .thenReturn(
            new BookingResponse(
                bookingId,
                "hub-1",
                "kart-1",
                startsAt,
                endsAt,
                "ACTIVE",
                "Booking confirmed."));

    mockMvc
        .perform(
            post("/api/bookings")
                .header("Authorization", "Bearer " + tokenForRole("END_USER"))
                .contentType("application/json")
                .content(
                    """
                    {"hubId":"hub-1","kartId":"kart-1","startsAt":"2099-01-01T10:00:00Z","endsAt":"2099-01-01T10:20:00Z"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(bookingId.toString()))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.message").value("Booking confirmed."));

    verify(bookingService)
        .book(
            new BookingRequest("hub-1", "kart-1", startsAt, endsAt),
            USER_ID);
  }

  @Requirement("KARTY-36")
  @Test
  void returnsMaximumDurationMessageWhenBookingIsTooLong() throws Exception {
    when(bookingService.book(any(BookingRequest.class), eq(USER_ID)))
        .thenThrow(
            new BookingValidationException("Maximum permitted rental duration: 60"));

    mockMvc
        .perform(
            post("/api/bookings")
                .header("Authorization", "Bearer " + tokenForRole("END_USER"))
                .contentType("application/json")
                .content(bookingJson()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Maximum permitted rental duration: 60"));
  }

  @Requirement("KARTY-36")
  @Test
  void returnsUnavailableMessageWhenKartWasBookedFirst() throws Exception {
    when(bookingService.book(any(BookingRequest.class), eq(USER_ID)))
        .thenThrow(new KartUnavailableException());

    mockMvc
        .perform(
            post("/api/bookings")
                .header("Authorization", "Bearer " + tokenForRole("END_USER"))
                .contentType("application/json")
                .content(bookingJson()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("The kart is no longer available."));
  }

  @Requirement("KARTY-36")
  @Test
  void bookingEndpointsRequireAnAuthenticatedEndUser() throws Exception {
    mockMvc.perform(post("/api/bookings")).andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            get("/api/bookings/active")
                .header("Authorization", "Bearer " + tokenForRole("OPERATOR")))
        .andExpect(status().isForbidden());
  }

  @Requirement("KARTY-36")
  @Test
  void endUserCanListActiveBookings() throws Exception {
    when(bookingService.activeBookings(USER_ID))
        .thenReturn(
            List.of(
                new BookingResponse(
                    UUID.fromString("10000000-0000-0000-0000-000000000036"),
                    "hub-1",
                    "kart-1",
                    Instant.parse("2099-01-01T10:00:00Z"),
                    Instant.parse("2099-01-01T10:20:00Z"),
                    "ACTIVE",
                    null)));

    mockMvc
        .perform(
            get("/api/bookings/active")
                .header("Authorization", "Bearer " + tokenForRole("END_USER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].kartId").value("kart-1"))
        .andExpect(jsonPath("$[0].status").value("ACTIVE"));

    verify(bookingService).activeBookings(USER_ID);
  }

  private String bookingJson() {
    return """
        {"hubId":"hub-1","kartId":"kart-1","startsAt":"2099-01-01T10:00:00Z","endsAt":"2099-01-01T10:20:00Z"}
        """;
  }

  private String tokenForRole(String role) {
    var now = Instant.now();
    when(userAccountRepository.findById(USER_ID))
        .thenReturn(
            Optional.of(
                new UserAccount(
                    USER_ID,
                    "karty-36-test-user",
                    "karty-36-test-user@example.com",
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
