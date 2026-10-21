package com.xjicloud.admin;

import java.util.UUID;

public record EciManagerTaskResponse(
        UUID id,
        String status,
        String instanceStatus,
        String cloudStatus,
        String statusReason,
        boolean cleanupConfirmed,
        String failureReason
) {

    public EciManagerTaskResponse(UUID id, String status) {
        this(id, status, null, null, null, false, null);
    }
}
