package pt.ua.kartylicious.bookings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import pt.ua.kartylicious.hubs.Kart;
import pt.ua.kartylicious.hubs.KartStatus;
import pt.ua.kartylicious.hubs.port.HubRepository;

@ExtendWith(MockitoExtension.class)
class ReservationLifecycleServiceTest {

  private static final Instant NOW = Instant.parse("2099-01-01T10:00:00Z");
  private static final String HUB_ID = "hub-1";
  private static final String KART_ID = "kart-1";
  private static final UUID USER_ID = UUID.randomUUID();

  @Mock private BookingRepository bookingRepository;
  @Mock private HubRepository hubRepository;
  @Mock private PaymentPort paymentPort;

  private ReservationLifecycleService service;

  @BeforeEach
  void setUp() {
    service =
        new ReservationLifecycleService(
            bookingRepository,
            hubRepository,
            paymentPort,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private void givenReservedKartAtHub() {
    when(hubRepository.lockKart(HUB_ID, KART_ID))
        .thenReturn(
            Optional.of(
                new Kart(KART_ID, "Kart 1", 80, KartStatus.RESERVED, true, false)));
  }

  @Test
  @Requirement("KARTY-36")
  void expiresEndedBookingReleasesItsDepositAndKart() {
    givenReservedKartAtHub();
    var ended = booking(NOW.minusSeconds(600), NOW);
    when(bookingRepository.findActiveByKart(HUB_ID, KART_ID))
        .thenReturn(List.of(ended))
        .thenReturn(List.of());

    assertThat(service.activeByKart(HUB_ID, KART_ID)).isEmpty();

    verify(bookingRepository)
        .save(
            new Booking(
                ended.id(),
                ended.userId(),
                ended.hubId(),
                ended.kartId(),
                ended.startsAt(),
                ended.endsAt(),
                BookingStatus.EXPIRED));
    verify(paymentPort).releaseSecurityDeposit(USER_ID, ended.id());
    verify(hubRepository).reconcileReservedKart(KART_ID, false);
  }

  @Test
  @Requirement("KARTY-36")
  void keepsKartReservedWhenAnotherFutureBookingExists() {
    givenReservedKartAtHub();
    var ended = booking(NOW.minusSeconds(600), NOW.minusSeconds(1));
    var future = booking(NOW.plusSeconds(600), NOW.plusSeconds(1_200));
    when(bookingRepository.findActiveByKart(HUB_ID, KART_ID))
        .thenReturn(List.of(ended, future))
        .thenReturn(List.of(future));
    when(bookingRepository.hasUnexpiredReservation(KART_ID, NOW)).thenReturn(true);

    assertThat(service.activeByKart(HUB_ID, KART_ID)).containsExactly(future);
    verify(paymentPort).releaseSecurityDeposit(USER_ID, ended.id());
    verify(paymentPort, never()).releaseSecurityDeposit(USER_ID, future.id());
    verify(hubRepository, never()).reconcileReservedKart(KART_ID, false);
    verify(hubRepository).reconcileReservedKart(KART_ID, true);
  }

  @Test
  @Requirement("KARTY-40")
  void cancellationRejectsAnotherUsersReservation() {
    var active = booking(NOW.minusSeconds(1), NOW.plusSeconds(600));
    when(bookingRepository.findById(active.id())).thenReturn(Optional.of(active));

    assertThatThrownBy(() -> service.cancel(active.id(), UUID.randomUUID()))
        .isInstanceOf(BookingAccessDeniedException.class);

    verifyNoInteractions(hubRepository, paymentPort);
  }

  @Test
  @Requirement("KARTY-40")
  void cancellationRejectsReservationAtItsExpiryBoundary() {
    var expired = booking(NOW.minusSeconds(600), NOW);
    when(bookingRepository.findById(expired.id())).thenReturn(Optional.of(expired));
    when(hubRepository.lockKartById(KART_ID))
        .thenReturn(Optional.of(new Kart(KART_ID, "Kart 1", 80, KartStatus.RESERVED, true, false)));

    assertThatThrownBy(() -> service.cancel(expired.id(), USER_ID))
        .isInstanceOf(ReservationNotCancellableException.class);

    verify(bookingRepository, never()).save(any());
    verifyNoInteractions(paymentPort);
  }

  private Booking booking(Instant startsAt, Instant endsAt) {
    return new Booking(
        UUID.randomUUID(),
        USER_ID,
        HUB_ID,
        KART_ID,
        startsAt,
        endsAt,
        BookingStatus.ACTIVE);
  }
}
