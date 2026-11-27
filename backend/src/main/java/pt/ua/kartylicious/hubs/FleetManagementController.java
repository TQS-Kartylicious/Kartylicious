package pt.ua.kartylicious.hubs;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/operator")
@PreAuthorize("hasRole('OPERATOR')")
public class FleetManagementController {
  private final FleetManagementService fleet;

  public FleetManagementController(FleetManagementService fleet) {
    this.fleet = fleet;
  }

  @GetMapping("/hubs")
  public List<HubResponse> hubs() {
    return fleet.hubs().stream().map(hub -> HubResponse.from(hub, null)).toList();
  }

  @PostMapping("/hubs")
  @ResponseStatus(HttpStatus.CREATED)
  public HubResponse registerHub(@RequestBody HubRequest input) {
    var hub = fleet.registerHub(input.name(), input.address(), input.integerCapacity());
    return HubResponse.from(hub, "Hub registered.");
  }

  @PutMapping("/hubs/{hubId}")
  public HubResponse updateHub(@PathVariable String hubId, @RequestBody HubRequest input) {
    var hub = fleet.updateHub(hubId, input.name(), input.address(), input.integerCapacity());
    return HubResponse.from(hub, "Hub updated.");
  }

  @PostMapping("/karts")
  @ResponseStatus(HttpStatus.CREATED)
  public KartResponse registerKart(@RequestBody KartRequest input) {
    return new KartResponse(fleet.registerKart(input.id(), input.hubId()), "Mini-kart registered.");
  }

  @PatchMapping("/karts/{kartId}")
  public KartResponse updateKart(@PathVariable String kartId, @RequestBody ActiveRequest input) {
    var kart = fleet.setKartActive(kartId, input.active());
    return new KartResponse(kart, kart.active() ? "Mini-kart activated." : "Mini-kart deactivated.");
  }

  @ExceptionHandler(FleetManagementService.Invalid.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> invalid(FleetManagementService.Invalid error) {
    return Map.of("message", error.getMessage());
  }

  @ExceptionHandler(FleetManagementService.Missing.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public Map<String, String> notFound(FleetManagementService.Missing error) {
    return Map.of("message", error.getMessage());
  }

  @ExceptionHandler(FleetManagementService.Conflict.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String, String> conflict(FleetManagementService.Conflict error) {
    return Map.of("message", error.getMessage());
  }

  public record HubRequest(String name, String address, JsonNode capacity) {
    Integer integerCapacity() {
      return capacity != null && capacity.isIntegralNumber() && capacity.canConvertToInt()
          ? capacity.intValue() : null;
    }
  }

  public record KartRequest(String id, String hubId) {}

  public record ActiveRequest(Boolean active) {}

  public record KartResponse(Kart kart, String message) {}

  public record HubResponse(
      String id, String name, String address, int capacity, long occupancy, List<Kart> karts,
      String message) {
    static HubResponse from(Hub hub, String message) {
      return new HubResponse(
          hub.id(), hub.name(), hub.address(), hub.capacity(), hub.occupancy(), hub.karts(), message);
    }
  }
}
