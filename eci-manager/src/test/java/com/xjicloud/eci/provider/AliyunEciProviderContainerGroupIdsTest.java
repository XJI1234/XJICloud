package com.xjicloud.eci.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Describe API 的 ContainerGroupIds JSON 必须安全编码，禁止字符串拼接。
 */
class AliyunEciProviderContainerGroupIdsTest {

    @Test
    void encodesPlainIdAsJsonArray() {
        assertThat(AliyunEciProvider.toContainerGroupIdsJson("eci-abc-123"))
                .isEqualTo("[\"eci-abc-123\"]");
    }

    @Test
    void escapesQuotesAndBackslashes() {
        assertThat(AliyunEciProvider.toContainerGroupIdsJson("id\"with\\chars"))
                .isEqualTo("[\"id\\\"with\\\\chars\"]");
    }

    @Test
    void rejectsBlankId() {
        assertThatThrownBy(() -> AliyunEciProvider.toContainerGroupIdsJson("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sanitizeNameCollapsesNonAlphanumericToValidPrefix() {
        String name = AliyunEciProvider.sanitizeName("!!!");
        assertThat(name).matches("^eci-demo-[0-9a-f]{8}$");
    }

    @Test
    void sanitizeNameStartsWithLetterAndKeepsAlnumDash() {
        String name = AliyunEciProvider.sanitizeName("My_Task--01");
        assertThat(name).matches("^my-task-01-[0-9a-f]{8}$");
    }

    @Test
    void sanitizeNameUsesDefaultWhenBlank() {
        String name = AliyunEciProvider.sanitizeName("   ");
        assertThat(name).matches("^eci-demo-[0-9a-f]{8}$");
    }
}
