package com.xjicloud.job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateDatasetRequest(
        @NotBlank String name,
        @NotEmpty List<DatasetFileRequest> files
) {
}
