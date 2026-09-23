package com.xjicloud.eci.lifecycle.action;

import java.util.UUID;

public record ClaimedLifecycleAction(
        UUID actionId,
        UUID instanceId,
        LifecycleActionType actionType,
        LifecycleActionPhase phase,
        UUID leaseToken) {
}
