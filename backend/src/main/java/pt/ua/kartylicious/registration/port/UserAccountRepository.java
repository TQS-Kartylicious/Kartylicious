package pt.ua.kartylicious.registration.port;

import pt.ua.kartylicious.registration.UserAccount;

public interface UserAccountRepository {

  boolean existsByEmail(String email);

  boolean existsByUsername(String username);

  UserAccount save(UserAccount account);
}
