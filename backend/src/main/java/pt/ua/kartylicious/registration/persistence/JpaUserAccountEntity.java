package pt.ua.kartylicious.registration.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import pt.ua.kartylicious.registration.UserRole;

@Entity
@Table(name = "user_accounts")
public class JpaUserAccountEntity {

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 64)
  private String username;

  @Column(nullable = false, unique = true, length = 254)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private UserRole role;

  @Column(nullable = false, columnDefinition = "boolean default true")
  private boolean active = true;

  protected JpaUserAccountEntity() {}

  JpaUserAccountEntity(
      UUID id,
      String username,
      String email,
      String passwordHash,
      UserRole role) {
    this(id, username, email, passwordHash, role, true);
  }

  JpaUserAccountEntity(
      UUID id,
      String username,
      String email,
      String passwordHash,
      UserRole role,
      boolean active) {
    this.id = id;
    this.username = username;
    this.email = email;
    this.passwordHash = passwordHash;
    this.role = role;
    this.active = active;
  }

  UUID getId() {
    return id;
  }

  String getUsername() {
    return username;
  }

  String getEmail() {
    return email;
  }

  String getPasswordHash() {
    return passwordHash;
  }

  UserRole getRole() {
    return role;
  }

  boolean isActive() {
    return active;
  }

  void setRole(UserRole role) {
    this.role = role;
  }

  void setActive(boolean active) {
    this.active = active;
  }
}
