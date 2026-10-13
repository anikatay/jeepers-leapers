package com.neueda.leap.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import java.time.OffsetDateTime;

public record RegisterRequest (
    @NotEmpty (message = "First name is required")
    @Pattern(regexp = "^[a-zA-Z\\s-]+$", message = "First name must contain only letters, spaces, or hyphens")
    String firstName,

    @NotEmpty (message = "Last name is required")
    @Pattern(regexp = "^[a-zA-Z\\s-]+$", message = "Last name must contain only letters, spaces, or hyphens")
    String lastName,

    @NotNull (message = "Date of birth is required")
    @Past (message = "Date of birth must be in the past")
    OffsetDateTime dateOfBirth, 

    @NotEmpty (message = "SSN is required")
    @Pattern(regexp = "^(\\d{3}-?\\d{2}-?\\d{4})$", message = "SSN must be in the format XXX-XX-XXXX")
    String ssn,

    @NotEmpty (message = "Address is required")
    String address,

    @NotEmpty (message = "Phone number is required")
    @Pattern(regexp = "^(\\(\\d{3}\\)\\s?\\d{3}-\\d{4}|\\d{10})$", message = "Phone number must be in the format (123) 456-7890 or 1234567890")
    String phoneNumber,

    @NotEmpty (message = "Email is required")
    @Pattern(regexp = "^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$", message = "Email must be in a valid format")
    String email,

    @NotEmpty (message = "Password is required")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$", message = "Password must be minimum eight characters, at least one letter and one number")
    String password
) {}