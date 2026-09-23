package com.xjicloud.eci.provider;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockEciProviderIdempotencyTest {

    @Test
    void createsOnlyOneCloudInstanceForTheSameClientToken() {
        MockEciProvider provider = new MockEciProvider();
        CreateSpec spec = spec();

        String first = provider.create(spec, "local-instance-1");
        String second = provider.create(spec, "local-instance-1");

        assertThat(second).isEqualTo(first);
        assertThat(provider.getCloudInstanceCount()).isEqualTo(1);
    }

    @Test
    void createsDifferentCloudInstancesForDifferentClientTokens() {
        MockEciProvider provider = new MockEciProvider();

        String first = provider.create(spec(), "local-instance-1");
        String second = provider.create(spec(), "local-instance-2");

        assertThat(second).isNotEqualTo(first);
        assertThat(provider.getCloudInstanceCount()).isEqualTo(2);
    }

    @Test
    void returnsCreatedCloudIdAfterUnknownOutcomeForTheSameClientToken() {
        MockEciProvider provider = new MockEciProvider();
        provider.failNextCreateAfterAccepted("local-instance-1");

        assertThatThrownBy(() -> provider.create(spec(), "local-instance-1"))
                .isInstanceOf(CloudOperationException.class)
                .extracting(error -> ((CloudOperationException) error).kind())
                .isEqualTo(CloudOperationKind.UNKNOWN_OUTCOME);

        String cloudId = provider.create(spec(), "local-instance-1");

        assertThat(cloudId).startsWith("mock-eci-");
        assertThat(provider.getCloudInstanceCount()).isEqualTo(1);
    }

    private static CreateSpec spec() {
        return new CreateSpec("idempotency-test", "nginx:alpine", 1.0, 2.0,
                Map.of(), null, null, "Never", null, null, null, null);
    }
}
