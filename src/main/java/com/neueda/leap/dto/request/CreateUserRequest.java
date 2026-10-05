package com.neueda.leap.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import java.time.OffsetDateTime;

public record CreateUserRequest(
    
    @NotNull (message = "User ID is required")
    UUID userId,

    @NotEmpty (message = "Role is required")
    String role,

    @NotEmpty (message = "Status is required")
    String status,

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
    String email
) {}