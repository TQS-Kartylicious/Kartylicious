package pt.ua.kartylicious.admin;

import jakarta.validation.constraints.NotNull;

public record StatusRequest(@NotNull(message = "The active status is required") Boolean active) {}
