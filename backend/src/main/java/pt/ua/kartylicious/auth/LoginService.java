package pt.ua.kartylicious.auth;

import java.util.Locale;
import org.springframework.stereotype.Service;
import pt.ua.kartylicious.auth.port.JwtTokenIssuer;
import pt.ua.kartylicious.registration.UserAccount;
import pt.ua.kartylicious.registration.port.UserAccountRepository;
import pt.ua.kartylicious.registration.security.PasswordHasher;

@Service
public class LoginService {

  private static final String DUMMY_PASSWORD_HASH =
      "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

  private final UserAccountRepository userAccountRepository;
  private final PasswordHasher passwordHasher;
  private final JwtTokenIssuer jwtTokenIssuer;

  public LoginService(
      UserAccountRepository userAccountRepository,
      PasswordHasher passwordHasher,
      JwtTokenIssuer jwtTokenIssuer) {
    this.userAccountRepository = userAccountRepository;
    this.passwordHasher = passwordHasher;
    this.jwtTokenIssuer = jwtTokenIssuer;
  }

  public LoginResponse login(String email, String password) {
    var normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
    var account = userAccountRepository.findByEmail(normalizedEmail);
    var passwordMatches =
        passwordHasher.matches(
            password,
            account.map(UserAccount::passwordHash).orElse(DUMMY_PASSWORD_HASH));

    if (account.isEmpty() || !passwordMatches) {
      throw new InvalidCredentialsException();
    }

    var issued = jwtTokenIssuer.issue(account.orElseThrow());
    var user = account.orElseThrow();
    return new LoginResponse(
        issued.token(),
        "Bearer",
        issued.expiresInSeconds(),
        new LoginResponse.UserResponse(
            user.id(), user.username(), user.email(), user.role()));
  }
}
