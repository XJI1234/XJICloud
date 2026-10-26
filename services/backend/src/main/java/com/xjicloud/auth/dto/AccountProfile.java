package com.xjicloud.auth.dto;

import java.util.UUID;

public record AccountProfile(UUID userId, String username, String phone, String displayName,
                             String email, String organization, boolean profileRequired) {}
