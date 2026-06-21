package com.xjicloud.worker.dto;

import jakarta.validation.constraints.NotBlank;

public record JobFailRequest(@NotBlank String errorMessage) {
}
