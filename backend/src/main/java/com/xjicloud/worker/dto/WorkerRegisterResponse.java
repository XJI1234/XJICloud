package com.xjicloud.worker.dto;

import java.util.UUID;

public record WorkerRegisterResponse(UUID workerId, String token) {
}
