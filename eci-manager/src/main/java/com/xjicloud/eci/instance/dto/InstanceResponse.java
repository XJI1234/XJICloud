package com.xjicloud.eci.instance.dto;

import com.xjicloud.eci.instance.InstanceStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 实例详情/列表项响应。
 *
 * @param id               本地 UUID
 * @param cloudInstanceId  云侧容器组 ID（或 mock-eci-...）
 * @param cloudStatus      最近一次云侧状态字符串
 * @param failureReason    失败原因（仅 FAILED 等场景）
 */
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
