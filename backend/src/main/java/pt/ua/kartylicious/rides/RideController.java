package pt.ua.kartylicious.rides;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
}
