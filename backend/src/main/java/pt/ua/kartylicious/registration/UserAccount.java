package pt.ua.kartylicious.registration;

import java.util.UUID;

public record UserAccount(
    UUID id,
    String username,
    String email,
    String passwordHash,
    UserRole role,
    boolean active) {

  public UserAccount(
      UUID id,
      String username,
      String email,
      String passwordHash,
      UserRole role) {
    this(id, username, email, passwordHash, role, true);
  }
}
