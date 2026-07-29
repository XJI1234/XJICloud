package com.xjicloud.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank @Size(max = 256) String name,
        @Size(max = 2000) String description
) {}
