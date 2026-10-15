package com.neueda.leap.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateUserRequest(
    
    @NotNull (message = "User ID is required")
    UUID userId,

    @NotEmpty (message = "Role is required")
    String role,

    @NotEmpty (message = "Status is required")
    String status,
) {}