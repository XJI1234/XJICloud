package com.xjicloud.eci.credential;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccessKeyPairTest {

    @Test
    void completePairRequiresBothSides() {
        assertThat(new AccessKeyPair("LTAItestkey01", "secretsecret").isComplete()).isTrue();
        assertThat(new AccessKeyPair("  LTAItestkey01  ", "  secretsecret  ").isComplete()).isTrue();
    }

    @Test
    void incompleteWhenEitherSideBlank() {
        assertThat(new AccessKeyPair("LTAItestkey01", "").isComplete()).isFalse();
        assertThat(new AccessKeyPair("LTAItestkey01", "   ").isComplete()).isFalse();
        assertThat(new AccessKeyPair("", "secretsecret").isComplete()).isFalse();
        assertThat(new AccessKeyPair(null, "secretsecret").isComplete()).isFalse();
        assertThat(new AccessKeyPair("LTAItestkey01", null).isComplete()).isFalse();
        assertThat(new AccessKeyPair(null, null).isComplete()).isFalse();
    }
}
