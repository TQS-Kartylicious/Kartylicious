package pt.ua.kartylicious.registration;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/registration")
public class RegistrationController {

  private final RegistrationService registrationService;

  public RegistrationController(RegistrationService registrationService) {
    this.registrationService = registrationService;
  }

  @PostMapping
  public ResponseEntity<RegistrationResponse> register(
      @Valid @RequestBody RegistrationRequest request) {
    var account =
        registrationService.register(request.username(), request.email(), request.password());
    var response =
        new RegistrationResponse(account.id(), account.username(), account.email(), account.role());

    return ResponseEntity.created(URI.create("/api/users/" + account.id())).body(response);
  }
}
