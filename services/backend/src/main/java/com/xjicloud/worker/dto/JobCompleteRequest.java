package com.xjicloud.worker.dto;

import jakarta.validation.constraints.NotBlank;

public record JobCompleteRequest(@NotBlank String outputOssKey) {
}
