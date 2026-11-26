package pt.ua.kartylicious.rides;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rides")
@PreAuthorize("hasRole('END_USER')")
public class RideController {
  private final RideService rideService;

  public RideController(RideService rideService) {
    this.rideService = rideService;
  }

  @PostMapping
  public RideResponse start(
      @RequestBody RideRequest request, @AuthenticationPrincipal Jwt jwt) {
    return rideService.start(request, UUID.fromString(jwt.getSubject()));
  }

  @GetMapping("/active")
  public ResponseEntity<RideResponse> activeRide(@AuthenticationPrincipal Jwt jwt) {
    return rideService.activeRide(UUID.fromString(jwt.getSubject()))
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @PatchMapping("/{rideId}")
  public RideCompletionResponse end(
      @PathVariable UUID rideId,
      @RequestBody RideCompletionRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return rideService.end(rideId, request, UUID.fromString(jwt.getSubject()));
  }
}
