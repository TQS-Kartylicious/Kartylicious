package pt.ua.kartylicious.auth;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.kartylicious.registration.port.UserAccountRepository;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final LoginService loginService;
  private final UserAccountRepository userAccountRepository;

  public AuthController(
      LoginService loginService, UserAccountRepository userAccountRepository) {
    this.loginService = loginService;
    this.userAccountRepository = userAccountRepository;
  }

  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    return loginService.login(request.user(), request.password());
  }

  @GetMapping("/me")
  public LoginResponse.UserResponse me(@AuthenticationPrincipal Jwt jwt) {
    var account =
        userAccountRepository
            .findById(UUID.fromString(jwt.getSubject()))
            .orElseThrow();
    return new LoginResponse.UserResponse(
        account.id(), account.username(), account.email(), account.role());
  }
}
