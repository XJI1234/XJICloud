package com.xjicloud.model.dto;

import jakarta.validation.constraints.NotBlank;

public record RestoreModelVersionRequest(
        @NotBlank String archiveName
) {
}
