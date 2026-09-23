package com.xjicloud.eci.lifecycle.action;

import java.time.Instant;

public final class LifecycleActionBackoff {

    private LifecycleActionBackoff() {
    }

    public static Instant nextAttemptAt(Instant now, int attemptCount, int initialSec, int maxSec) {
        if (attemptCount <= 1) {
            return now.plusSeconds(initialSec);
        }

        long delaySec = initialSec;
        for (int attempt = 1; attempt < attemptCount && delaySec < maxSec; attempt++) {
            delaySec = Math.min((long) maxSec, delaySec * 2L);
        }
        return now.plusSeconds(Math.min(delaySec, maxSec));
    }
}
