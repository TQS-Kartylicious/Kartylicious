package pt.ua.kartylicious.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.kartylicious.auth.port.JwtTokenIssuer;
import pt.ua.kartylicious.registration.UserAccount;
import pt.ua.kartylicious.registration.UserRole;
import pt.ua.kartylicious.registration.port.UserAccountRepository;
import pt.ua.kartylicious.registration.security.PasswordHasher;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

  @Mock private UserAccountRepository repository;
  @Mock private PasswordHasher passwordHasher;
  @Mock private JwtTokenIssuer tokenIssuer;

  private LoginService service;
  private UserAccount account;

  @BeforeEach
  void setUp() {
    when(passwordHasher.hash(anyString())).thenReturn("dummy-hash");
    service = new LoginService(repository, passwordHasher, tokenIssuer);
    account =
        new UserAccount(
            UUID.fromString("6a62f8c5-f4a4-4c54-827e-4e31b6ef728e"),
            "kartfan",
            "kart@example.com",
            "hashed-password",
            UserRole.END_USER);
  }

  @Requirement("KARTY-34")
  @Test
  void loginSucceedsWithNormalizedEmailAndMatchingPassword() {
    when(repository.findByEmail("kart@example.com")).thenReturn(Optional.of(account));
    when(passwordHasher.matches("strong-pass-123", "hashed-password")).thenReturn(true);
    when(tokenIssuer.issue(account))
        .thenReturn(new JwtTokenIssuer.IssuedToken("signed-token", 3600));

    var result = service.login("  KART@Example.com  ", "strong-pass-123");

    assertThat(result.token()).isEqualTo("signed-token");
    assertThat(result.tokenType()).isEqualTo("Bearer");
    assertThat(result.expiresIn()).isEqualTo(3600);
    assertThat(result.user().role()).isEqualTo(UserRole.END_USER);
  }

  @Requirement("KARTY-34")
  @Test
  void unknownEmailThrowsTheSameInvalidCredentialsException() {
    when(repository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
    when(passwordHasher.matches(anyString(), anyString())).thenReturn(false);

    assertThatThrownBy(() -> service.login("unknown@example.com", "strong-pass-123"))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage("Incorrect email or password.");

    verify(tokenIssuer, never()).issue(account);
  }

  @Requirement("KARTY-34")
  @Test
  void wrongPasswordThrowsTheSameInvalidCredentialsException() {
    when(repository.findByEmail("kart@example.com")).thenReturn(Optional.of(account));
    when(passwordHasher.matches("wrong-password", "hashed-password")).thenReturn(false);

    assertThatThrownBy(() -> service.login("kart@example.com", "wrong-password"))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage("Incorrect email or password.");

    verify(tokenIssuer, never()).issue(account);
  }

  @Requirement("KARTY-76")
  @Test
  void deactivatedAccountWithCorrectPasswordIsForbidden() {
    var inactive =
        new UserAccount(
            account.id(),
            account.username(),
            account.email(),
            "hashed-password",
            UserRole.END_USER,
            false);
    when(repository.findByEmail("kart@example.com")).thenReturn(Optional.of(inactive));
    when(passwordHasher.matches("strong-pass-123", "hashed-password")).thenReturn(true);

    assertThatThrownBy(() -> service.login("kart@example.com", "strong-pass-123"))
        .isInstanceOf(DeactivatedAccountException.class)
        .hasMessage("This account is deactivated.");

    org.mockito.Mockito.verify(tokenIssuer, never()).issue(inactive);
  }
}
