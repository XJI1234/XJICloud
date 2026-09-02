package com.xjicloud.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateUploadSessionRequest(
        @NotBlank String fileName,
        @Positive long sizeBytes
) {}
