package com.neueda.leap.dto.response;

import java.util.UUID;

public record AuthResponse(
    String token, 
    UUID userId, 
    UUID accountId,
    String role
) {}