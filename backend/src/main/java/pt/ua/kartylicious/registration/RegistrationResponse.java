package pt.ua.kartylicious.registration;

import java.util.UUID;

public record RegistrationResponse(UUID id, String username, String email, UserRole role) {}
