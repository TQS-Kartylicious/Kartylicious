package pt.ua.kartylicious.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "The email is required") String user,
    @NotBlank(message = "The password is required") String password) {}
