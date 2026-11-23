package pt.ua.kartylicious.hubs;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import pt.ua.kartylicious.hubs.port.HubRepository;

@ExtendWith(MockitoExtension.class)
class HubDirectoryServiceTest {

  @Mock private HubRepository hubRepository;

  private HubDirectoryService service;

  @BeforeEach
  void setUp() {
    service = new HubDirectoryService(hubRepository);
  }

  @Requirement("KARTY-35")
  @Test
  void kartsThatAreNotAvailableCannotBeSelectedForBooking() {
    var hub =
        new Hub(
            "hub-1",
            "Riverside Hub",
            "1 River Road",
            List.of(
                new Kart("kart-0", "Yellow kart", 80, KartStatus.LOW_BATTERY, true, false),
                new Kart("kart-4", "White kart", 80, KartStatus.INACTIVE, true, false),
                new Kart("kart-1", "Blue kart", 80, KartStatus.IN_USE, true, false),
                new Kart("kart-2", "Green kart", 80, KartStatus.RESERVED, true, false),
                new Kart(
                    "kart-3", "Red kart", 80, KartStatus.IN_MAINTENANCE, true, false),
                new Kart("kart-5", "Orange kart", 80, KartStatus.RETIRED, true, false)));
    when(hubRepository.findById("hub-1")).thenReturn(Optional.of(hub));

    var result = service.getHub("hub-1");

    assertThat(result.karts()).extracting(HubDirectoryService.KartDetails::selectable)
        .containsOnly(false);
  }

  @Requirement("KARTY-35")
  @Test
  void kartAtMinimumBatteryLevelCanBeSelectedForBooking() {
    var hub = hubWithKart(new Kart("kart-1", "Blue kart", 20, KartStatus.AVAILABLE, true, false));
    when(hubRepository.findById("hub-1")).thenReturn(Optional.of(hub));

    var result = service.getHub("hub-1");

    assertThat(result.karts()).singleElement()
        .satisfies(
            kart -> {
              assertThat(kart.batteryLevel()).isEqualTo(20);
              assertThat(kart.selectable()).isTrue();
            });
  }

  @Requirement("KARTY-35")
  @Test
  void kartAtMaximumBatteryLevelCanBeSelectedForBooking() {
    var hub = hubWithKart(new Kart("kart-1", "Blue kart", 100, KartStatus.AVAILABLE, true, false));
    when(hubRepository.findById("hub-1")).thenReturn(Optional.of(hub));

    var result = service.getHub("hub-1");

    assertThat(result.karts()).singleElement()
        .satisfies(
            kart -> {
              assertThat(kart.batteryLevel()).isEqualTo(100);
              assertThat(kart.selectable()).isTrue();
            });
  }

  @Requirement("KARTY-35")
  @Test
  void kartBelowMinimumBatteryLevelCannotBeSelectedForBooking() {
    var hub = hubWithKart(new Kart("kart-1", "Blue kart", 19, KartStatus.AVAILABLE, true, false));
    when(hubRepository.findById("hub-1")).thenReturn(Optional.of(hub));

    var result = service.getHub("hub-1");

    assertThat(result.karts()).singleElement()
        .satisfies(
            kart -> {
              assertThat(kart.batteryLevel()).isEqualTo(19);
              assertThat(kart.selectable()).isFalse();
            });
  }

  @Requirement("KARTY-35")
  @Test
  void kartAtZeroBatteryCannotBeSelectedForBooking() {
    var hub = hubWithKart(new Kart("kart-1", "Blue kart", 0, KartStatus.AVAILABLE, true, false));
    when(hubRepository.findById("hub-1")).thenReturn(Optional.of(hub));

    var result = service.getHub("hub-1");

    assertThat(result.karts()).singleElement()
        .satisfies(
            kart -> {
              assertThat(kart.batteryLevel()).isZero();
              assertThat(kart.selectable()).isFalse();
            });
  }

  private Hub hubWithKart(Kart kart) {
    return new Hub("hub-1", "Riverside Hub", "1 River Road", List.of(kart));
  }
}
