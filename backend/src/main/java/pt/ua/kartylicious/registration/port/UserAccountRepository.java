package pt.ua.kartylicious.registration.port;

import java.util.Optional;
import java.util.UUID;
import pt.ua.kartylicious.registration.UserAccount;

public interface UserAccountRepository {

  boolean existsByEmail(String email);

  boolean existsByUsername(String username);

  Optional<UserAccount> findByEmail(String email);

  Optional<UserAccount> findById(UUID id);

  UserAccount save(UserAccount account);
}
