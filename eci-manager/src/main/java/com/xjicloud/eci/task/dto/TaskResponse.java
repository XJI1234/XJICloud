package com.xjicloud.eci.task.dto;

import com.xjicloud.eci.task.TaskStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 任务详情/列表项响应（含落库后的实际规格）。
 */
public record TaskResponse(
        UUID id,
        String name,
        TaskStatus status,
        UUID instanceId,
        String image,
        List<String> command,
        List<String> args,
        double cpu,
        double memory,
        String instanceType,
        Integer gpu,
        String imageSnapshotId,
        String restartPolicy,
        int maxRuntimeSec,
        Map<String, String> env,
        String failureReason,
        String containerLogTail,
        Instant startedAt,
        Instant endedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
