package com.xjicloud.eci.provider;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mock Provider：restartPolicy / command / args 记录，以及终态模拟钩子。
 */
class EciProviderRestartPolicyTest {

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
                List.of("--mode", "3dgs"));

        String cloudId = provider.create(spec);

        assertThat(cloudId).startsWith("mock-eci-");
        assertThat(provider.getCreateSpec(cloudId)).isSameAs(spec);
        assertThat(provider.getCreateSpec(cloudId).restartPolicy()).isEqualTo("OnFailure");
        assertThat(provider.getLastCreateSpec().command()).containsExactly("python", "train.py");
        assertThat(provider.getLastCreateSpec().args()).containsExactly("--mode", "3dgs");
        assertThat(provider.describe(cloudId).isRunning()).isTrue();
    }

    @Test
    void mockSimulateStatusTransitionsToSucceededAndFailed() {
        MockEciProvider provider = new MockEciProvider();
        String cloudId = provider.create(new CreateSpec(
                "term",
                "nginx:alpine",
                1.0,
                2.0,
                Map.of(),
                null,
                null,
                "Never",
                null,
                null));

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
    void mockSimulateStatusRejectsUnknownId() {
        MockEciProvider provider = new MockEciProvider();
        assertThatThrownBy(() -> provider.simulateStatus("mock-eci-missing", "Succeeded"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }
}
