package pt.ua.kartylicious.registration;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import pt.ua.kartylicious.registration.port.UserAccountRepository;
import pt.ua.kartylicious.registration.security.PasswordHasher;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

  @Mock private UserAccountRepository userAccountRepository;

  @Mock private PasswordHasher passwordHasher;

  private RegistrationService registrationService;

  @BeforeEach
  void setUp() {
    registrationService = new RegistrationService(userAccountRepository, passwordHasher);
  }

  @Requirement("KARTY-27")
  @Test
  void registerCreatesAnEndUserWithHashedPassword() {
    var password = "strong-pass-123";
    when(userAccountRepository.existsByEmail("kart@example.com")).thenReturn(false);
    when(userAccountRepository.existsByUsername("kartfan")).thenReturn(false);
    when(passwordHasher.hash(password)).thenReturn("hashed-password");
    when(userAccountRepository.save(any(UserAccount.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var result = registrationService.register("kartfan", "kart@example.com", password);

    assertThat(result.id()).isNotNull();
    assertThat(result.username()).isEqualTo("kartfan");
    assertThat(result.email()).isEqualTo("kart@example.com");
    assertThat(result.role()).isEqualTo(UserRole.END_USER);
    verify(passwordHasher).hash(password);

    var captor = ArgumentCaptor.forClass(UserAccount.class);
    verify(userAccountRepository).save(captor.capture());
    assertThat(captor.getValue().passwordHash()).isEqualTo("hashed-password");
    assertThat(captor.getValue().passwordHash()).isNotEqualTo(password);
  }

  @Requirement("KARTY-27")
  @Test
  void registerRejectsAnEmailThatIsAlreadyInUse() {
    when(userAccountRepository.existsByEmail("kart@example.com")).thenReturn(true);

    assertThatThrownBy(
            () -> registrationService.register("kartfan", "kart@example.com", "strong-pass-123"))
        .isInstanceOf(EmailAlreadyInUseException.class)
        .hasMessage("The email is already in use");

    verify(userAccountRepository, never()).save(any());
  }

  @Requirement("KARTY-27")
  @Test
  void registerRejectsAUsernameThatIsAlreadyInUse() {
    when(userAccountRepository.existsByEmail("kart@example.com")).thenReturn(false);
    when(userAccountRepository.existsByUsername("kartfan")).thenReturn(true);

    assertThatThrownBy(
            () -> registrationService.register("kartfan", "kart@example.com", "strong-pass-123"))
        .isInstanceOf(UsernameAlreadyInUseException.class);

    verify(userAccountRepository, never()).save(any());
  }

  @Requirement("KARTY-27")
  @Test
  void registerNormalizesEmailBeforeCheckingAndSaving() {
    when(userAccountRepository.existsByEmail("kart@example.com")).thenReturn(false);
    when(userAccountRepository.existsByUsername("kartfan")).thenReturn(false);
    when(passwordHasher.hash(any())).thenReturn("h");
    when(userAccountRepository.save(any(UserAccount.class)))
        .thenAnswer(i -> i.getArgument(0));

    var result = registrationService.register("kartfan", "  KART@Example.com ", "strong-pass-123");

    assertThat(result.email()).isEqualTo("kart@example.com");
  }
}
