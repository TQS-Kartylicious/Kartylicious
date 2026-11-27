package pt.ua.kartylicious.bookings;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import pt.ua.kartylicious.bookings.port.BookingRepository;

class ReservationExpirySchedulerTest {
  @Test
  @Requirement("KARTY-36")
  void usesApplicationClockAndContinuesAfterAnIndividualKartFails() {
    var now = Instant.parse("2099-01-01T10:00:00Z");
    var bookings = mock(BookingRepository.class);
    var lifecycle = mock(ReservationLifecycleService.class);
    when(bookings.findKartsWithExpiredReservations(now)).thenReturn(List.of("K-101", "K-102"));
    doThrow(new IllegalStateException("Synthetic expiry failure")).when(lifecycle).expireKart("K-101");

    new ReservationExpiryScheduler(bookings, lifecycle, Clock.fixed(now, ZoneOffset.UTC))
        .expireDueReservations();

    var calls = inOrder(bookings, lifecycle);
    calls.verify(bookings).findKartsWithExpiredReservations(now);
    calls.verify(lifecycle).expireKart("K-101");
    calls.verify(lifecycle).expireKart("K-102");
  }
}
