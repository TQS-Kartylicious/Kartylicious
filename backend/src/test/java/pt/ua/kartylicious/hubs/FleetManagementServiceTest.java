package pt.ua.kartylicious.hubs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.kartylicious.bookings.port.BookingRepository;
import pt.ua.kartylicious.hubs.port.HubRepository;

@ExtendWith(MockitoExtension.class)
class FleetManagementServiceTest {
  private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
  @Mock private HubRepository hubs;
  @Mock private BookingRepository bookings;

  @ParameterizedTest
  @ValueSource(ints = {-1, 0})
  @Requirement("KARTY-48")
  void rejectsNonPositiveCapacity(int capacity) {
    assertThatThrownBy(() -> service().registerHub("New", "Address", capacity))
        .hasMessage("Hub capacity must be a positive integer.");
    verify(hubs, never()).insertHub(any());
  }

  @Test
  @Requirement("KARTY-48")
  void acceptsCapacityOneAndRejectsMissingLocation() {
    assertThat(service().registerHub(" New ", " Address ", 1))
        .satisfies(hub -> {
          assertThat(hub.name()).isEqualTo("New");
          assertThat(hub.address()).isEqualTo("Address");
          assertThat(hub.capacity()).isEqualTo(1);
          assertThat(hub.occupancy()).isZero();
        });
    assertThatThrownBy(() -> service().registerHub("New", " ", 1))
        .hasMessageContaining("Hub location");
  }

  @Test
  @Requirement("KARTY-48")
  void countsParkedInactiveAndMaintenanceKartsButExcludesKartsInUse() {
    when(hubs.lockHub("hub")).thenReturn(Optional.of(new Hub("hub", "Old", "Address", 3, List.of(
        new Kart("a", "a", 20, KartStatus.INACTIVE, false, false),
        new Kart("b", "b", 20, KartStatus.IN_MAINTENANCE, true, true),
        new Kart("c", "c", 20, KartStatus.IN_USE, true, false)))));
    assertThatThrownBy(() -> service().updateHub("hub", "New", "New location", 1))
        .hasMessage("Hub capacity cannot be below current occupancy.");
    var updated = service().updateHub("hub", "New", "New location", 2);
    assertThat(updated.id()).isEqualTo("hub");
    assertThat(updated.occupancy()).isEqualTo(2);
    verify(hubs).updateHub(updated);
  }

  @Test
  @Requirement("KARTY-49")
  void startsRegisteredKartInactiveAndWithoutBatteryTelemetry() {
    when(hubs.lockHub("hub"))
        .thenReturn(Optional.of(new Hub("hub", "Hub", "Address", 1, List.of())));
    var kart = service().registerKart("K-new", "hub");
    assertThat(kart).isEqualTo(new Kart("K-new", "K-new", 0, KartStatus.INACTIVE, false, false));
    verify(hubs).insertKart("hub", kart);
  }

  @ParameterizedTest
  @ValueSource(ints = {19, 20, 21})
  @Requirement("KARTY-50")
  void enforcesBatteryThresholdOnActivation(int level) {
    givenKart(level, KartStatus.INACTIVE, false, false);
    if (level < 20) {
      assertThatThrownBy(() -> service().setKartActive("K-1", true))
          .hasMessageContaining("at least 20%");
      verify(hubs, never()).updateKartAvailability("K-1", true, KartStatus.AVAILABLE);
    } else {
      assertThat(service().setKartActive("K-1", true).status()).isEqualTo(KartStatus.AVAILABLE);
      verify(hubs).updateKartAvailability("K-1", true, KartStatus.AVAILABLE);
    }
  }

  @Test
  @Requirement("KARTY-50")
  void reactivationPreservesAnUnexpiredReservation() {
    givenKart(20, KartStatus.INACTIVE, false, false);
    when(bookings.hasUnexpiredReservation("K-1", NOW)).thenReturn(true);
    assertThat(service().setKartActive("K-1", true).status()).isEqualTo(KartStatus.RESERVED);
  }

  @Test
  @Requirement("KARTY-50")
  void cannotActivateAnInactiveKartWithCriticalFaults() {
    givenKart(21, KartStatus.INACTIVE, false, true);
    assertThatThrownBy(() -> service().setKartActive("K-1", true))
        .hasMessage("Resolve critical faults before activating this mini-kart.");
  }

  @Test
  @Requirement("KARTY-50")
  void deactivationDoesNotClearMaintenanceRestrictions() {
    givenKart(21, KartStatus.IN_MAINTENANCE, true, false);
    var deactivated = service().setKartActive("K-1", false);
    assertThat(deactivated.active()).isFalse();
    assertThat(deactivated.status()).isEqualTo(KartStatus.IN_MAINTENANCE);
    givenKart(21, KartStatus.IN_MAINTENANCE, false, false);
    assertThatThrownBy(() -> service().setKartActive("K-1", true))
        .hasMessage("Complete maintenance before activating this mini-kart.");
  }

  @Test
  @Requirement("KARTY-50")
  void cannotDeactivateAnInUseKart() {
    givenKart(21, KartStatus.IN_USE, true, false);
    assertThatThrownBy(() -> service().setKartActive("K-1", false))
        .hasMessage("A mini-kart in use cannot be deactivated.");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  @Requirement("KARTY-50")
  void rejectsBothAvailabilityUpdatesForRetiredKarts(boolean active) {
    givenKart(21, KartStatus.RETIRED, false, false);
    assertThatThrownBy(() -> service().setKartActive("K-1", active))
        .hasMessage("Retired mini-karts cannot be reactivated.");
  }

  private void givenKart(int battery, KartStatus status, boolean active, boolean fault) {
    when(hubs.lockKartById("K-1"))
        .thenReturn(Optional.of(new Kart("K-1", "K-1", battery, status, active, fault)));
  }

  private FleetManagementService service() {
    return new FleetManagementService(hubs, bookings, Clock.fixed(NOW, ZoneOffset.UTC));
  }
}
