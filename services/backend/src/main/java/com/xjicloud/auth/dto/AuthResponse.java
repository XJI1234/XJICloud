package com.xjicloud.auth.dto;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInMs,
        UUID userId,
        String username,
        String displayName
) {}
