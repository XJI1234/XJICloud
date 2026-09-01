package com.xjicloud.model.dto;

import jakarta.validation.constraints.NotBlank;

public record SaveViewerConfigRequest(@NotBlank String jsonPayload) {}
