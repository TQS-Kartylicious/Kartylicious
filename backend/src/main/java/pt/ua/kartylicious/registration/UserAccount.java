package pt.ua.kartylicious.registration;

import java.util.UUID;

public record UserAccount(
    UUID id, String username, String email, String passwordHash, UserRole role) {}
