package com.xjicloud.worker.dto;

import jakarta.validation.constraints.NotBlank;

public record WorkerRegisterRequest(@NotBlank String name, String gpuInfo) {
}
