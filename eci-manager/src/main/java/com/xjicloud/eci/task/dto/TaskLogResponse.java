package com.xjicloud.eci.task.dto;

import com.xjicloud.eci.task.TaskStatus;

import java.util.UUID;

/**
 * {@code GET /tasks/{id}/logs} 的 data。{@code source} 为 {@code LIVE} / {@code PERSISTED} / {@code EMPTY}。
 */
public record TaskLogResponse(
        UUID taskId,
        TaskStatus status,
        String source,
        String content,
        int tailLines,
        String note
) {
}
