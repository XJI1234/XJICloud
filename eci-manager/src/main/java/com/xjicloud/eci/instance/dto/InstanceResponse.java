package com.xjicloud.eci.instance.dto;

import com.xjicloud.eci.instance.InstanceStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record InstanceResponse(
        UUID id,
        String name,
        String image,
        double cpu,
        double memory,
        InstanceStatus status,
        String cloudInstanceId,
        String cloudStatus,
        Map<String, String> env,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
}
