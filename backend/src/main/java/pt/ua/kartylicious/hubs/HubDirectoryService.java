package pt.ua.kartylicious.hubs;

import java.util.List;

import org.springframework.stereotype.Service;

import pt.ua.kartylicious.hubs.port.HubRepository;

@Service
public class HubDirectoryService {

  public static final int MINIMUM_BATTERY_LEVEL = 20;

  private final HubRepository hubRepository;

  public HubDirectoryService(HubRepository hubRepository) {
    this.hubRepository = hubRepository;
  }

  public List<HubSummary> listHubs() {
    return hubRepository.findAll().stream()
        .map(
            hub ->
                new HubSummary(
                    hub.id(),
                    hub.name(),
                    hub.address(),
                    (int) hub.karts().stream().filter(this::isSelectable).count()))
        .toList();
  }

  public HubDetails getHub(String id) {
    var hub = hubRepository.findById(id).orElseThrow(() -> new HubNotFoundException(id));
    var karts =
        hub.karts().stream()
            .map(
                kart ->
                    new KartDetails(
                        kart.id(),
                        kart.name(),
                        kart.batteryLevel(),
                        displayStatus(kart).displayName(),
                        isSelectable(kart)))
            .toList();
    return new HubDetails(hub.id(), hub.name(), hub.address(), karts);
  }

  private boolean isSelectable(Kart kart) {
    return kart.active()
        && !kart.criticalFault()
        && kart.batteryLevel() >= MINIMUM_BATTERY_LEVEL
        && displayStatus(kart) == KartStatus.AVAILABLE;
  }


  private KartStatus displayStatus(Kart kart) {
    if (!kart.active()
        && (kart.status() == KartStatus.AVAILABLE
            || kart.status() == KartStatus.RESERVED
            || kart.status() == KartStatus.LOW_BATTERY)) {
      return KartStatus.INACTIVE;
    }
    if (kart.criticalFault()
        && (kart.status() == KartStatus.AVAILABLE
            || kart.status() == KartStatus.LOW_BATTERY
            || kart.status() == KartStatus.RESERVED)) {
      return KartStatus.IN_MAINTENANCE;
    }
    if (kart.status() == KartStatus.AVAILABLE
        && kart.batteryLevel() < MINIMUM_BATTERY_LEVEL) {
      return KartStatus.LOW_BATTERY;
    }
    if (kart.status() == KartStatus.LOW_BATTERY
        && kart.active()
        && kart.batteryLevel() >= MINIMUM_BATTERY_LEVEL) {
      return KartStatus.AVAILABLE;
    }
    return kart.status();
  }

  public record HubSummary(String id, String name, String address, int availableKartCount) {}

  public record HubDetails(String id, String name, String address, List<KartDetails> karts) {}

  public record KartDetails(
      String id, String name, int batteryLevel, String status, boolean selectable) {}
}
