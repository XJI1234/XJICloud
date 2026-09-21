package com.xjicloud.model.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RestoreModelVersionRequest(
        @NotNull UUID versionId
) {
}
