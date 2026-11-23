package pt.ua.kartylicious.admin;

import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.kartylicious.registration.UserAccount;
import pt.ua.kartylicious.registration.UserRole;
import pt.ua.kartylicious.registration.port.UserAccountRepository;
import pt.ua.kartylicious.registration.security.PasswordHasher;

@Component
public class AdminBootstrap implements ApplicationRunner {

  private static final Logger LOGGER = LoggerFactory.getLogger(AdminBootstrap.class);

  private final Environment environment;
  private final UserAccountRepository repository;
  private final PasswordHasher passwordHasher;

  public AdminBootstrap(
      Environment environment,
      UserAccountRepository repository,
      PasswordHasher passwordHasher) {
    this.environment = environment;
    this.repository = repository;
    this.passwordHasher = passwordHasher;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (repository.countAdmins() > 0) {
      return;
    }

    var username = environment.getProperty("admin.username", "").strip();
    var email = environment.getProperty("admin.email", "").strip();
    var password = environment.getProperty("admin.password", "");

    if (username.isBlank() || email.isBlank() || password.isBlank()) {
      LOGGER.warn("No admin account was seeded because ADMIN_* bootstrap variables are missing");
      return;
    }
    if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        || password.length() < 8) {
      LOGGER.warn("No admin account was seeded because ADMIN_* bootstrap values are invalid");
      return;
    }
    if (repository.existsByEmail(email.toLowerCase(Locale.ROOT))
        || repository.existsByUsername(username.toLowerCase(Locale.ROOT))) {
      LOGGER.warn("No admin account was seeded because the bootstrap email or username is in use");
      return;
    }

    repository.save(
        new UserAccount(
            UUID.randomUUID(),
            username.toLowerCase(Locale.ROOT),
            email.toLowerCase(Locale.ROOT),
            passwordHasher.hash(password),
            UserRole.ADMIN,
            true));
  }
}
