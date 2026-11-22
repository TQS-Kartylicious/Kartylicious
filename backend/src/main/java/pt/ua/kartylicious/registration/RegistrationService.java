package pt.ua.kartylicious.registration;

import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.kartylicious.registration.port.UserAccountRepository;
import pt.ua.kartylicious.registration.security.PasswordHasher;

@Service
public class RegistrationService {

  private final UserAccountRepository userAccountRepository;
  private final PasswordHasher passwordHasher;

  public RegistrationService(
      UserAccountRepository userAccountRepository, PasswordHasher passwordHasher) {
    this.userAccountRepository = userAccountRepository;
    this.passwordHasher = passwordHasher;
  }

  @Transactional
  public UserAccount register(String username, String email, String password) {
    var normalizedUsername = username.strip().toLowerCase(Locale.ROOT);
    var normalizedEmail = email.strip().toLowerCase(Locale.ROOT);

    if (userAccountRepository.existsByEmail(normalizedEmail)) {
      throw new EmailAlreadyInUseException();
    }
    if (userAccountRepository.existsByUsername(normalizedUsername)) {
      throw new UsernameAlreadyInUseException();
    }

    var account =
        new UserAccount(
            UUID.randomUUID(),
            normalizedUsername,
            normalizedEmail,
            passwordHasher.hash(password),
            UserRole.END_USER);
    return userAccountRepository.save(account);
  }
}
