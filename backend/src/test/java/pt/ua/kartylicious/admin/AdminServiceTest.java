package pt.ua.kartylicious.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.kartylicious.admin.port.UserActivityPort;
import pt.ua.kartylicious.registration.UserAccount;
import pt.ua.kartylicious.registration.UserRole;
import pt.ua.kartylicious.registration.port.UserAccountRepository;
import pt.ua.kartylicious.registration.security.PasswordHasher;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

  @Mock private UserAccountRepository repository;
  @Mock private PasswordHasher passwordHasher;
  @Mock private UserActivityPort userActivityPort;

  private AdminService service;

  @BeforeEach
  void setUp() {
    service = new AdminService(repository, passwordHasher, userActivityPort);
  }

  @Requirement("KARTY-76")
  @Test
  void createsAllowedStaffRoleWithNormalizedValuesAndHash() {
    when(repository.existsByEmail("staff@example.com")).thenReturn(false);
    when(repository.existsByUsername("staff")).thenReturn(false);
    when(passwordHasher.hash("strong-pass-123")).thenReturn("hash");
    when(repository.save(any(UserAccount.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var result =
        service.createStaff(
            new AdminRequest(
                " Staff ", " STAFF@Example.com ", "strong-pass-123", "OPERATOR"));

    assertThat(result.username()).isEqualTo("staff");
    assertThat(result.email()).isEqualTo("staff@example.com");
    assertThat(result.role()).isEqualTo(UserRole.OPERATOR);
    assertThat(result.active()).isTrue();
  }

  @Requirement("KARTY-76")
  @Test
  void rejectsEndUserStaffCreation() {
    assertThatThrownBy(
            () ->
                service.createStaff(
                    new AdminRequest(
                        "staff", "staff@example.com", "strong-pass-123", "END_USER")))
        .isInstanceOf(AdminExceptions.InvalidRoleException.class);
  }

  @Requirement("KARTY-76")
  @Test
  void rejectsDuplicateEmailAndUsername() {
    when(repository.existsByEmail("staff@example.com")).thenReturn(true);
    assertThatThrownBy(
            () ->
                service.createStaff(
                    new AdminRequest(
                        "staff", "staff@example.com", "strong-pass-123", "OPERATOR")))
        .isInstanceOf(AdminExceptions.EmailAlreadyInUseException.class);

    when(repository.existsByEmail("staff@example.com")).thenReturn(false);
    when(repository.existsByUsername("staff")).thenReturn(true);
    assertThatThrownBy(
            () ->
                service.createStaff(
                    new AdminRequest(
                        "staff", "staff@example.com", "strong-pass-123", "OPERATOR")))
        .isInstanceOf(AdminExceptions.UsernameAlreadyInUseException.class);
  }

  @Requirement("KARTY-76")
  @Test
  void changesRole() {
    var id = UUID.randomUUID();
    var account =
        new UserAccount(id, "staff", "staff@example.com", "hash", UserRole.END_USER, true);
    when(repository.lockActiveAdmins()).thenReturn(List.of());
    when(repository.findById(id)).thenReturn(Optional.of(account));
    when(repository.save(any(UserAccount.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var result = service.changeRole(id, "MUNICIPAL_WORKER");

    assertThat(result.role()).isEqualTo(UserRole.MUNICIPAL_WORKER);
  }

  @Requirement("KARTY-76")
  @Test
  void blocksLastActiveAdminDemotionAndSelfDeactivation() {
    var id = UUID.randomUUID();
    var admin =
        new UserAccount(id, "admin", "admin@example.com", "hash", UserRole.ADMIN, true);
    when(repository.lockActiveAdmins()).thenReturn(List.of(admin));
    when(repository.findById(id)).thenReturn(Optional.of(admin));
    when(repository.countActiveAdmins()).thenReturn(1L);

    assertThatThrownBy(() -> service.changeRole(id, "END_USER"))
        .isInstanceOf(AdminExceptions.LastActiveAdminException.class);
    assertThatThrownBy(() -> service.deactivate(id))
        .isInstanceOf(AdminExceptions.LastActiveAdminException.class);
  }

  @Requirement("KARTY-76")
  @Test
  void deactivationIsIdempotentAndBlockedByActivity() {
    var id = UUID.randomUUID();
    var inactive =
        new UserAccount(id, "staff", "staff@example.com", "hash", UserRole.OPERATOR, false);
    when(repository.lockActiveAdmins()).thenReturn(List.of());
    when(repository.findById(id)).thenReturn(Optional.of(inactive));

    assertThat(service.deactivate(id).active()).isFalse();

    var active =
        new UserAccount(id, "staff", "staff@example.com", "hash", UserRole.OPERATOR, true);
    when(repository.findById(id)).thenReturn(Optional.of(active));
    when(userActivityPort.hasActiveRideOrReservation(id)).thenReturn(true);
    assertThatThrownBy(() -> service.deactivate(id))
        .isInstanceOf(AdminExceptions.ActiveRideOrReservationException.class);
  }
}
