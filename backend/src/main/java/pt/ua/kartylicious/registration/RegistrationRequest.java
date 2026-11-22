package pt.ua.kartylicious.registration;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
    @NotBlank(message = "The username is required") String username,
    @NotBlank(message = "The email is required")
        @Email(message = "The email format is incorrect")
        String email,
    @NotBlank(message = "The password is required")
        @Size(min = 8, message = "The password must be equal or longer than 8 characters")
        String password) {}
