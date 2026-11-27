package pt.ua.kartylicious.hubs;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.kartylicious.bookings.port.BookingRepository;
import pt.ua.kartylicious.faults.FaultService;
import pt.ua.kartylicious.hubs.port.HubRepository;

@ExtendWith(MockitoExtension.class)
@Tag("BR14")
class FleetTelemetryServiceTest {
  private static final Instant NOW = Instant.parse("2099-01-01T10:00:00Z");

  @Mock private HubRepository hubs;
  @Mock private BookingRepository bookings;
  @Mock private FaultService faults;
  @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  @InjectMocks private FleetTelemetryService service;

  @Test
  void admitsAThresholdReadingAtDatabasePrecisionWithoutOpeningATicket() {
    when(hubs.lockKartById("K-101")).thenReturn(Optional.of(
        new Kart("K-101", "Kart", 19, KartStatus.LOW_BATTERY, true, false)));
    when(bookings.hasUnexpiredReservation("K-101", NOW)).thenReturn(true);
    var time = NOW.minusSeconds(1);

    service.ingest(new TelemetryObservation("K-101", null, 20, true, false, time.plusNanos(999)));

    verify(hubs).recordTelemetry(new TelemetryObservation("K-101", null, 20, true, false, time), true);
    verifyNoInteractions(faults);
  }

  @Test
  void rejectsFutureDatedReadingsBeforeTouchingTheFleet() {
    assertThatThrownBy(
            () -> service.ingest(new TelemetryObservation("K-101", null, 20, true, false,
                NOW.plusNanos(1))))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(hubs, bookings, faults);
  }
}
