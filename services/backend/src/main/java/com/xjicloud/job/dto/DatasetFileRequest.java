package com.xjicloud.job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record DatasetFileRequest(
        @NotBlank String archivedName,
        @NotBlank String originalName,
        @NotBlank String contentType,
        @Positive long sizeBytes
) {
}
