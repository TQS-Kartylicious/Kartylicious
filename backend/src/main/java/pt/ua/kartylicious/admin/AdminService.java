package pt.ua.kartylicious.admin;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.kartylicious.admin.port.UserActivityPort;
import pt.ua.kartylicious.registration.UserAccount;
import pt.ua.kartylicious.registration.UserRole;
import pt.ua.kartylicious.registration.port.UserAccountRepository;
import pt.ua.kartylicious.registration.security.PasswordHasher;

@Service
public class AdminService {

  private final UserAccountRepository repository;
  private final PasswordHasher passwordHasher;
  private final UserActivityPort userActivityPort;

  public AdminService(
      UserAccountRepository repository,
      PasswordHasher passwordHasher,
      UserActivityPort userActivityPort) {
    this.repository = repository;
    this.passwordHasher = passwordHasher;
    this.userActivityPort = userActivityPort;
  }

  @Transactional
  public AdminUserResponse createStaff(AdminRequest request) {
    var username = request.username().strip().toLowerCase(Locale.ROOT);
    var email = request.email().strip().toLowerCase(Locale.ROOT);
    var role = staffRole(request.role());

    if (repository.existsByEmail(email)) {
      throw new AdminExceptions.EmailAlreadyInUseException();
    }
    if (repository.existsByUsername(username)) {
      throw new AdminExceptions.UsernameAlreadyInUseException();
    }

    var account =
        repository.save(
            new UserAccount(
                UUID.randomUUID(),
                username,
                email,
                passwordHasher.hash(request.password()),
                role,
                true));
    return toResponse(account);
  }

  public List<AdminUserResponse> listUsers() {
    return repository.findAllOrderedByUsername().stream().map(this::toResponse).toList();
  }

  @Transactional
  public AdminUserResponse changeRole(UUID id, String roleName) {
    repository.lockActiveAdmins();
    var target =
        repository.findById(id).orElseThrow(AdminExceptions.UserNotFoundException::new);
    var newRole = allRole(roleName);

    if (target.role() == UserRole.ADMIN
        && newRole != UserRole.ADMIN
        && repository.countActiveAdmins() <= 1) {
      throw new AdminExceptions.LastActiveAdminException();
    }

    var updated =
        new UserAccount(
            target.id(),
            target.username(),
            target.email(),
            target.passwordHash(),
            newRole,
            target.active());
    return toResponse(repository.save(updated));
  }

  @Transactional
  public AdminUserResponse deactivate(UUID id) {
    repository.lockActiveAdmins();
    var target =
        repository.findById(id).orElseThrow(AdminExceptions.UserNotFoundException::new);
    if (!target.active()) {
      return toResponse(target);
    }
    if (userActivityPort.hasActiveRideOrReservation(id)) {
      throw new AdminExceptions.ActiveRideOrReservationException();
    }
    if (target.role() == UserRole.ADMIN && repository.countActiveAdmins() <= 1) {
      throw new AdminExceptions.LastActiveAdminException();
    }

    var updated =
        new UserAccount(
            target.id(),
            target.username(),
            target.email(),
            target.passwordHash(),
            target.role(),
            false);
    return toResponse(repository.save(updated));
  }

  private UserRole staffRole(String roleName) {
    var role = allRole(roleName);
    if (role == UserRole.END_USER) {
      throw new AdminExceptions.InvalidRoleException();
    }
    return role;
  }

  private UserRole allRole(String roleName) {
    try {
      return UserRole.valueOf(roleName.strip().toUpperCase(Locale.ROOT).replace(' ', '_'));
    } catch (RuntimeException exception) {
      throw new AdminExceptions.InvalidRoleException();
    }
  }

  private AdminUserResponse toResponse(UserAccount account) {
    return new AdminUserResponse(
        account.id(), account.username(), account.email(), account.role(), account.active());
  }
}
