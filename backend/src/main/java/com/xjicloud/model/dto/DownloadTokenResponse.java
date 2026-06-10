package com.xjicloud.model.dto;

import java.time.Instant;

public record DownloadTokenResponse(String url, Instant expiresAt) {
}
