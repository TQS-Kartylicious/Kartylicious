package pt.ua.kartylicious.admin;

import jakarta.validation.constraints.NotBlank;

public record RoleRequest(@NotBlank(message = "The role is required") String role) {}
