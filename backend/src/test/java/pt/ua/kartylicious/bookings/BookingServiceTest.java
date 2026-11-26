package pt.ua.kartylicious.bookings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.kartylicious.bookings.port.BookingRepository;
import pt.ua.kartylicious.bookings.port.PaymentPort;
import pt.ua.kartylicious.config.MunicipalRulesProperties;
import pt.ua.kartylicious.hubs.Hub;
import pt.ua.kartylicious.hubs.Kart;
import pt.ua.kartylicious.hubs.KartStatus;
import pt.ua.kartylicious.hubs.port.HubRepository;
import pt.ua.kartylicious.registration.UserAccount;
import pt.ua.kartylicious.registration.UserRole;
import pt.ua.kartylicious.registration.port.UserAccountRepository;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

  private static final String HUB_ID = "hub-1";
  private static final String KART_ID = "kart-1";
  private static final UUID FIRST_USER = UUID.randomUUID();
  private static final UUID SECOND_USER = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2099-01-01T10:00:00Z");

  @Mock private HubRepository hubRepository;

  @Mock private BookingRepository bookingRepository;

  @Mock private PaymentPort paymentPort;

  @Mock private UserAccountRepository userAccountRepository;

  @Mock private ReservationLifecycleService reservationLifecycleService;

  private BookingService bookingService;

  @BeforeEach
  void setUp() {
    var municipalRules = new MunicipalRulesProperties();
    municipalRules.setMaximumReservationDurationMinutes(60);
    bookingService =
        new BookingService(
            hubRepository,
            bookingRepository,
            paymentPort,
            userAccountRepository,
            reservationLifecycleService,
            municipalRules,
            Clock.fixed(NOW, ZoneOffset.UTC));
    givenKart(new Kart(KART_ID, "Kart 1", 80, KartStatus.AVAILABLE, true, false));
    lenient()
        .when(reservationLifecycleService.activeByKart(HUB_ID, KART_ID))
        .thenReturn(List.of());
    lenient().when(paymentPort.authorizeSecurityDeposit(any(), any())).thenReturn(true);
    lenient().when(hubRepository.reserveKart(HUB_ID, KART_ID)).thenReturn(true);
    lenient()
        .when(userAccountRepository.lockById(any()))
        .thenAnswer(
            invocation ->
                Optional.of(
                    new UserAccount(
                        invocation.getArgument(0),
                        "rider",
                        "rider@example.com",
                        "hash",
                        UserRole.END_USER)));
  }

  @Requirement("KARTY-36")
  @Test
  void booksAnAvailableKartAndAddsItToTheUserActiveTrips() {
    var request = requestAt(10, 20);

    var response = bookingService.book(request, FIRST_USER);

    assertThat(response.status()).isEqualTo(BookingStatus.ACTIVE.name());
    assertThat(response.message()).isEqualTo("Booking confirmed.");
    verify(paymentPort).authorizeSecurityDeposit(any(), any());
    verify(hubRepository).reserveKart(HUB_ID, KART_ID);
    verify(bookingRepository).save(any(Booking.class));
  }

  @Requirement("KARTY-36")
  @Test
  void acceptsTheMinimumBatteryBoundary() {
    givenKart(new Kart(KART_ID, "Kart 1", 20, KartStatus.AVAILABLE, true, false));

    bookingService.book(requestAt(10, 20), FIRST_USER);

    verify(bookingRepository).save(any(Booking.class));
  }

  @Requirement("KARTY-36")
  @Test
  void rejectsAReservationLongerThanTheMaximumDuration() {
    var request = requestAt(10, 71);

    assertThatThrownBy(() -> bookingService.book(request, FIRST_USER))
        .isInstanceOf(BookingValidationException.class)
        .hasMessage("Maximum permitted rental duration: 60");

    verify(bookingRepository, never()).save(any(Booking.class));
    verify(hubRepository, never()).reserveKart(HUB_ID, KART_ID);
  }

  @Requirement("KARTY-36")
  @Test
  void acceptsTheMaximumDurationBoundary() {
    bookingService.book(requestAt(10, 70), FIRST_USER);

    verify(bookingRepository).save(any(Booking.class));
  }

  @Requirement("KARTY-36")
  @Test
  void rejectsANegativeDuration() {
    var request = requestAt(10, 9);

    assertThatThrownBy(() -> bookingService.book(request, FIRST_USER))
        .isInstanceOf(BookingValidationException.class)
        .hasMessage("Booking duration must be positive.");

    verify(bookingRepository, never()).save(any(Booking.class));
  }

  @Requirement("KARTY-36")
  @Test
  void preventsDoubleBookingWhenTheKartIsReservedBeforeTheSecondRequest() {
    var savedBookings = new ArrayList<Booking>();
    when(reservationLifecycleService.activeByKart(HUB_ID, KART_ID))
        .thenAnswer(invocation -> savedBookings);
    when(bookingRepository.save(any(Booking.class)))
        .thenAnswer(
            invocation -> {
              var booking = invocation.getArgument(0, Booking.class);
              savedBookings.add(booking);
              return booking;
            });
    when(hubRepository.reserveKart(HUB_ID, KART_ID)).thenReturn(true, false);

    bookingService.book(requestAt(10, 20), FIRST_USER);

    assertThatThrownBy(() -> bookingService.book(requestAt(15, 25), SECOND_USER))
        .isInstanceOf(KartUnavailableException.class)
        .hasMessage("The kart is no longer available.");

    assertThat(savedBookings).hasSize(1);
    assertThat(savedBookings.getFirst().userId()).isEqualTo(FIRST_USER);
    verify(bookingRepository).save(any(Booking.class));
  }

  @Requirement("KARTY-36")
  @Test
  void allowsConsecutiveBookingsWhenTheNextStartsAtThePreviousEnd() {
    var savedBookings = new ArrayList<Booking>();
    when(reservationLifecycleService.activeByKart(HUB_ID, KART_ID))
        .thenAnswer(invocation -> savedBookings);
    when(bookingRepository.save(any(Booking.class)))
        .thenAnswer(
            invocation -> {
              var booking = invocation.getArgument(0, Booking.class);
              savedBookings.add(booking);
              return booking;
            });

    bookingService.book(requestAt(10, 20), FIRST_USER);
    bookingService.book(requestAt(20, 30), SECOND_USER);

    assertThat(savedBookings).hasSize(2);
  }

  @Requirement("KARTY-36")
  @Test
  void rejectsABookingThatStartsBeforeThePreviousBookingEnds() {
    var existing = bookingAt(10, 20, FIRST_USER);
    when(reservationLifecycleService.activeByKart(HUB_ID, KART_ID))
        .thenReturn(List.of(existing));

    assertThatThrownBy(() -> bookingService.book(requestAt(19, 29), SECOND_USER))
        .isInstanceOf(KartUnavailableException.class)
        .hasMessage("The kart is no longer available.");

    verify(bookingRepository, never()).save(any(Booking.class));
  }

  @Requirement("KARTY-36")
  @Test
  void rejectsAnUnavailableKart() {
    givenKart(new Kart(KART_ID, "Kart 1", 80, KartStatus.RESERVED, true, false));

    assertUnavailable();
  }

  @Requirement("KARTY-36")
  @Test
  void rejectsAnInactiveKart() {
    givenKart(new Kart(KART_ID, "Kart 1", 80, KartStatus.AVAILABLE, false, false));

    assertUnavailable();
  }

  @Requirement("KARTY-36")
  @Test
  void rejectsABookingWhenTheLockedUserAccountIsInactive() {
    when(userAccountRepository.lockById(FIRST_USER))
        .thenReturn(
            Optional.of(
                new UserAccount(
                    FIRST_USER,
                    "rider",
                    "rider@example.com",
                    "hash",
                    UserRole.END_USER,
                    false)));

    assertThatThrownBy(() -> bookingService.book(requestAt(10, 20), FIRST_USER))
        .isInstanceOf(BookingValidationException.class)
        .hasMessage("User account is inactive.");

    verify(hubRepository, never()).reserveKart(HUB_ID, KART_ID);
  }

  @Requirement("KARTY-36")
  @Test
  void rejectsABookableKartBelowTheMinimumBattery() {
    givenKart(new Kart(KART_ID, "Kart 1", 19, KartStatus.AVAILABLE, true, false));

    assertUnavailable();
  }

  @Requirement("KARTY-36")
  @Test
  void rejectsAKartWithACriticalFault() {
    givenKart(new Kart(KART_ID, "Kart 1", 80, KartStatus.AVAILABLE, true, true));

    assertUnavailable();
  }

  @Requirement("KARTY-36")
  @Test
  void propagatesPersistenceFailureToRollBackTheDeposit() {
    doThrow(new IllegalStateException("database unavailable"))
        .when(bookingRepository)
        .save(any(Booking.class));

    assertThatThrownBy(() -> bookingService.book(requestAt(10, 20), FIRST_USER))
        .isInstanceOf(IllegalStateException.class);

    verify(paymentPort).authorizeSecurityDeposit(any(), any());
  }

  @Requirement("KARTY-36")
  @Test
  void usesTheConfiguredMaximumDuration() {
    var municipalRules = new MunicipalRulesProperties();
    municipalRules.setMaximumReservationDurationMinutes(30);
    bookingService =
        new BookingService(
            hubRepository,
            bookingRepository,
            paymentPort,
            userAccountRepository,
            reservationLifecycleService,
            municipalRules,
            Clock.fixed(NOW, ZoneOffset.UTC));

    assertThatThrownBy(() -> bookingService.book(requestAt(10, 41), FIRST_USER))
        .isInstanceOf(BookingValidationException.class)
        .hasMessage("Maximum permitted rental duration: 30");
  }

  private void assertUnavailable() {
    assertThatThrownBy(() -> bookingService.book(requestAt(10, 20), FIRST_USER))
        .isInstanceOf(KartUnavailableException.class)
        .hasMessage("The kart is no longer available.");

    verify(paymentPort, never()).authorizeSecurityDeposit(any(), any());
    verify(bookingRepository, never()).save(any(Booking.class));
  }

  private void givenKart(Kart kart) {
    lenient()
        .when(hubRepository.findById(HUB_ID))
        .thenReturn(
            java.util.Optional.of(
                new Hub(HUB_ID, "Central Hub", "Main Street", 10, List.of(kart))));
  }

  private BookingRequest requestAt(long startMinute, long endMinute) {
    var start = NOW.plusSeconds(startMinute * 60);
    return new BookingRequest(
        HUB_ID, KART_ID, start, start.plusSeconds((endMinute - startMinute) * 60));
  }

  private Booking bookingAt(long startMinute, long endMinute, UUID userId) {
    var request = requestAt(startMinute, endMinute);
    return new Booking(
        UUID.randomUUID(),
        userId,
        request.hubId(),
        request.kartId(),
        request.startsAt(),
        request.endsAt(),
        BookingStatus.ACTIVE);
  }
}
