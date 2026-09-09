package com.xjicloud.eci.credential;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialHintTest {

    @Test
    void nullAndBlankReturnEmpty() {
        assertThat(CredentialHint.mask(null)).isEmpty();
        assertThat(CredentialHint.mask("")).isEmpty();
        assertThat(CredentialHint.mask("   ")).isEmpty();
    }

    @Test
    void eightCharsFullyMaskedAndDoesNotLeakHalves() {
        String hint = CredentialHint.mask("12345678");
        assertThat(hint).isEqualTo("****");
        assertThat(hint).doesNotContain("1234").doesNotContain("5678");
    }

    @Test
    void elevenCharsFullyMasked() {
        assertThat(CredentialHint.mask("12345678901")).isEqualTo("****");
    }

    @Test
    void twelveCharsKeepFirstFourAndLastFour() {
        String raw = "123456789012";
        String hint = CredentialHint.mask(raw);
        assertThat(hint).isEqualTo("1234****9012");
        assertThat(hint).doesNotContain("5678");
        assertThat(hint).isNotEqualTo(raw);
    }

    @Test
    void longerValueKeepsFirstFourAndLastFourWithoutFullSecret() {
        String raw = "LTAI5tExampleAbcd";
        String hint = CredentialHint.mask(raw);
        assertThat(hint).isEqualTo("LTAI****Abcd");
        assertThat(hint).doesNotContain(raw);
        assertThat(hint).doesNotContain("5tExample");
    }
}
