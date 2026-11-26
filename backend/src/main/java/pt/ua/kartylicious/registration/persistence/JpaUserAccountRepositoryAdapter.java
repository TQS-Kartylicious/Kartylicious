package pt.ua.kartylicious.registration.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
  public Optional<UserAccount> findByEmail(String email) {
    return repository.findByEmail(email).map(this::toAccount);
  }

  @Override
  public Optional<UserAccount> findById(UUID id) {
    return repository.findById(id).map(this::toAccount);
  }

  @Override
  public Optional<UserAccount> lockById(UUID id) {
    return repository.lockById(id).map(this::toAccount);
  }

  @Override
  public List<UserAccount> findAllOrderedByUsername() {
    return repository.findAllByOrderByUsernameAsc().stream().map(this::toAccount).toList();
  }

  @Override
  public long countActiveAdmins() {
    return repository.countByRoleAndActive(
        pt.ua.kartylicious.registration.UserRole.ADMIN, true);
  }

  @Override
  public long countAdmins() {
    return repository.countByRole(pt.ua.kartylicious.registration.UserRole.ADMIN);
  }

  @Override
  public List<UserAccount> lockActiveAdmins() {
    return repository.lockActiveAdmins().stream().map(this::toAccount).toList();
  }

  @Override
  public UserAccount save(UserAccount account) {
    var entity =
        new JpaUserAccountEntity(
            account.id(),
            account.username(),
            account.email(),
            account.passwordHash(),
            account.role(),
            account.active());
    var saved = repository.save(entity);

    return new UserAccount(
        saved.getId(),
        saved.getUsername(),
        saved.getEmail(),
        saved.getPasswordHash(),
        saved.getRole(),
        saved.isActive());
  }

  private UserAccount toAccount(JpaUserAccountEntity entity) {
    return new UserAccount(
        entity.getId(),
        entity.getUsername(),
        entity.getEmail(),
        entity.getPasswordHash(),
        entity.getRole(),
        entity.isActive());
  }
}
