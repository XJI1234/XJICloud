package com.xjicloud.eci.lifecycle.action;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LifecycleActionBackoffTest {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");

    @Test
    void calculatesExponentialRetryTimesAndCapsAtMaximum() {
        assertThat(LifecycleActionBackoff.nextAttemptAt(NOW, 1, 2, 300))
                .isEqualTo(NOW.plusSeconds(2));
        assertThat(LifecycleActionBackoff.nextAttemptAt(NOW, 2, 2, 300))
                .isEqualTo(NOW.plusSeconds(4));
        assertThat(LifecycleActionBackoff.nextAttemptAt(NOW, 8, 2, 300))
                .isEqualTo(NOW.plusSeconds(256));
        assertThat(LifecycleActionBackoff.nextAttemptAt(NOW, 20, 2, 300))
                .isEqualTo(NOW.plusSeconds(300));
    }

    @Test
    void usesInitialIntervalForNonPositiveAttempts() {
        assertThat(LifecycleActionBackoff.nextAttemptAt(NOW, 0, 2, 300))
                .isEqualTo(NOW.plusSeconds(2));
        assertThat(LifecycleActionBackoff.nextAttemptAt(NOW, -3, 2, 300))
                .isEqualTo(NOW.plusSeconds(2));
    }

    @Test
    void saturatesHugeAttemptCountWithoutOverflow() {
        assertThat(LifecycleActionBackoff.nextAttemptAt(NOW, Integer.MAX_VALUE, 2, 300))
                .isEqualTo(NOW.plusSeconds(300));
    }
}
