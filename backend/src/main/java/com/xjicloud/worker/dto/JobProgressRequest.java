package com.xjicloud.worker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record JobProgressRequest(
        @Min(0) @Max(100) int percent,
        @NotBlank String stage,
        String message
) {
}
