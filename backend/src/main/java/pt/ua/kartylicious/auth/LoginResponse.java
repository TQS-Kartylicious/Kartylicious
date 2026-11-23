package pt.ua.kartylicious.auth;

import java.util.UUID;
import pt.ua.kartylicious.registration.UserRole;

public record LoginResponse(
    String token,
    String tokenType,
    long expiresIn,
    UserResponse user) {

  public record UserResponse(UUID id, String username, String email, UserRole role) {}
}
