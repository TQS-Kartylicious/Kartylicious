package pt.ua.kartylicious.registration.persistence;

import org.springframework.stereotype.Repository;
import pt.ua.kartylicious.registration.UserAccount;
import pt.ua.kartylicious.registration.port.UserAccountRepository;

@Repository
public class JpaUserAccountRepositoryAdapter implements UserAccountRepository {

  private final SpringDataUserAccountRepository repository;

  public JpaUserAccountRepositoryAdapter(SpringDataUserAccountRepository repository) {
    this.repository = repository;
  }

  @Override
  public boolean existsByEmail(String email) {
    return repository.existsByEmail(email);
  }

  @Override
  public boolean existsByUsername(String username) {
    return repository.existsByUsername(username);
  }

  @Override
  public UserAccount save(UserAccount account) {
    var entity =
        new JpaUserAccountEntity(
            account.id(),
            account.username(),
            account.email(),
            account.passwordHash(),
            account.role());
    var saved = repository.save(entity);

    return new UserAccount(
        saved.getId(),
        saved.getUsername(),
        saved.getEmail(),
        saved.getPasswordHash(),
        saved.getRole());
  }
}
