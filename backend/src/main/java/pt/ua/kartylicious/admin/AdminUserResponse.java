package pt.ua.kartylicious.admin;

import java.util.UUID;
import pt.ua.kartylicious.registration.UserRole;

public record AdminUserResponse(
    UUID id, String username, String email, UserRole role, boolean active) {}
