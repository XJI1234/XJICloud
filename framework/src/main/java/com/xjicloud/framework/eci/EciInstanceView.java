package com.xjicloud.framework.eci;

import java.time.Instant;
import java.util.UUID;

public record EciInstanceView(
        UUID id,
        String containerGroupId,
        String name,
        EciStatus status,
        String linkedWorkerName,
        Instant createdAt,
        Instant updatedAt
) {
    static EciInstanceView from(EciInstance e) {
        return new EciInstanceView(
                e.getId(), e.getContainerGroupId(), e.getName(), e.getStatus(),
                e.getLinkedWorkerName(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
