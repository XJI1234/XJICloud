package com.xjicloud.eci.provider;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mock Provider：restartPolicy / command / args、终态钩子、时间触发自动终态。
 */
class EciProviderRestartPolicyTest {

    private static CreateSpec simpleSpec(String name, String restartPolicy) {
        return new CreateSpec(
                name,
                "nginx:alpine",
                1.0,
                2.0,
                Map.of(),
                null,
                null,
                restartPolicy,
                null,
                null,
                null);
    }

    @Test
    void mockCreateRecordsRestartPolicyAndCommandArgs() {
        MockEciProvider provider = new MockEciProvider();
        CreateSpec spec = new CreateSpec(
                "policy-demo",
                "nginx:alpine",
                1.0,
                2.0,
                Map.of(),
                null,
                null,
                "OnFailure",
                List.of("python", "train.py"),
                List.of("--mode", "3dgs"),
                null);

        String cloudId = provider.create(spec);

        assertThat(cloudId).startsWith("mock-eci-");
        assertThat(provider.getCreateSpec(cloudId)).isSameAs(spec);
        assertThat(provider.getCreateSpec(cloudId).restartPolicy()).isEqualTo("OnFailure");
        assertThat(provider.getLastCreateSpec().command()).containsExactly("python", "train.py");
        assertThat(provider.getLastCreateSpec().args()).containsExactly("--mode", "3dgs");
        assertThat(provider.describe(cloudId).isRunning()).isTrue();
    }

    @Test
    void mockCreateRecordsImageSnapshotId() {
        MockEciProvider provider = new MockEciProvider();
        CreateSpec spec = new CreateSpec(
                "cache-demo",
                "nginx:alpine",
                1.0,
                2.0,
                Map.of(),
                null,
                null,
                "Never",
                null,
                null,
                "imc-test-snapshot-id");

        String cloudId = provider.create(spec);

        assertThat(provider.getCreateSpec(cloudId).imageSnapshotId()).isEqualTo("imc-test-snapshot-id");
    }

    @Test
    void mockSimulateStatusTransitionsToSucceededAndFailed() {
        MockEciProvider provider = new MockEciProvider();
        String cloudId = provider.create(simpleSpec("term", "Never"));

        provider.simulateStatus(cloudId, "Succeeded");
        CloudInstanceView succeeded = provider.describe(cloudId);
        assertThat(succeeded.isSucceeded()).isTrue();
        assertThat(succeeded.isFailed()).isFalse();

        provider.simulateStatus(cloudId, "Failed");
        CloudInstanceView failed = provider.describe(cloudId);
        assertThat(failed.isFailed()).isTrue();
        assertThat(failed.isSucceeded()).isFalse();
    }

    @Test
    void mockAutoCompleteTransitionsAfterDelay() {
        AtomicLong now = new AtomicLong(1_000_000L);
        MockEciProvider provider = new MockEciProvider(1, "Succeeded", now::get);
        String cloudId = provider.create(simpleSpec("auto", "Never"));

        assertThat(provider.describe(cloudId).isRunning()).isTrue();
        now.addAndGet(1_100);
        assertThat(provider.describe(cloudId).isSucceeded()).isTrue();
    }

    @Test
    void mockSimulateStatusOverridesAutoComplete() {
        AtomicLong now = new AtomicLong(1_000_000L);
        MockEciProvider provider = new MockEciProvider(1, "Succeeded", now::get);
        String cloudId = provider.create(simpleSpec("override", "Never"));

        provider.simulateStatus(cloudId, "Failed");
        now.addAndGet(1_100);
        assertThat(provider.describe(cloudId).isFailed()).isTrue();
        assertThat(provider.describe(cloudId).isSucceeded()).isFalse();
    }

    @Test
    void mockAutoCompleteDisabledWhenSecZero() {
        AtomicLong now = new AtomicLong(1_000_000L);
        MockEciProvider provider = new MockEciProvider(0, "Succeeded", now::get);
        String cloudId = provider.create(simpleSpec("no-auto", "Never"));

        now.addAndGet(60_000);
        assertThat(provider.describe(cloudId).isRunning()).isTrue();
    }

    @Test
    void mockAutoCompleteCanTargetFailed() {
        AtomicLong now = new AtomicLong(1_000_000L);
        MockEciProvider provider = new MockEciProvider(1, "Failed", now::get);
        String cloudId = provider.create(simpleSpec("auto-fail", "Never"));

        now.addAndGet(1_100);
        assertThat(provider.describe(cloudId).isFailed()).isTrue();
    }

    @Test
    void mockDeletePreventsAutoCompleteOverwrite() {
        AtomicLong now = new AtomicLong(1_000_000L);
        MockEciProvider provider = new MockEciProvider(1, "Succeeded", now::get);
        String cloudId = provider.create(simpleSpec("deleted", "Never"));

        provider.delete(cloudId);
        now.addAndGet(1_100);
        CloudInstanceView view = provider.describe(cloudId);
        assertThat(view.isTerminatedOrMissing()).isTrue();
        assertThat(view.isSucceeded()).isFalse();
    }

    @Test
    void mockConstructorRejectsUnsupportedStatus() {
        assertThatThrownBy(() -> new MockEciProvider(1, "BogusStatus"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported");
    }

    @Test
    void mockSimulateStatusRejectsUnknownId() {
        MockEciProvider provider = new MockEciProvider();
        assertThatThrownBy(() -> provider.simulateStatus("mock-eci-missing", "Succeeded"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }
}
