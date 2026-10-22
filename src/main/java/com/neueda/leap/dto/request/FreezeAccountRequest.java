package com.neueda.leap.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FreezeAccountRequest(
        @NotBlank(message = "Freeze reason is required")
        @Size(max = 500, message = "Freeze reason must be 500 characters or fewer")
        String reason) {
}