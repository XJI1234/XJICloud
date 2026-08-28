package com.xjicloud.project.dto;

import jakarta.validation.constraints.Size;

public record UpdateProjectRequest(
        @Size(max = 256) String name,
        @Size(max = 2000) String description
) {}
