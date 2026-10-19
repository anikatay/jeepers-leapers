package com.neueda.leap.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ResolveInstrumentRequest(
        @NotBlank(message = "ticker is required")
        String ticker) {
}
