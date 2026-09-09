package com.xjicloud.eci.credential;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AliyunCredentialResolverTest {

    @Test
    void pairedAccessKeyBeatsRamRole() {
        assertThat(AliyunCredentialResolver.resolve("LTAItestkey01", "secretsecret", "eci-role"))
                .isEqualTo(AliyunCredentialMode.STATIC_AK);
    }

    @Test
    void roleWithoutAccessKeyUsesInstanceProfile() {
        assertThat(AliyunCredentialResolver.resolve(null, null, "eci-role"))
                .isEqualTo(AliyunCredentialMode.INSTANCE_PROFILE);
    }

    @Test
    void neitherAccessKeyNorRoleUsesDefaultChain() {
        assertThat(AliyunCredentialResolver.resolve(null, null, null))
                .isEqualTo(AliyunCredentialMode.DEFAULT_CHAIN);
        assertThat(AliyunCredentialResolver.resolve("  ", "  ", "  "))
                .isEqualTo(AliyunCredentialMode.DEFAULT_CHAIN);
    }

    @Test
    void incompletePairDoesNotUseStaticAk() {
        assertThat(AliyunCredentialResolver.resolve("LTAItestkey01", "", "eci-role"))
                .isEqualTo(AliyunCredentialMode.INSTANCE_PROFILE);
        assertThat(AliyunCredentialResolver.resolve("", "secretsecret", null))
                .isEqualTo(AliyunCredentialMode.DEFAULT_CHAIN);
        assertThat(AliyunCredentialResolver.resolve("LTAItestkey01", null, null))
                .isEqualTo(AliyunCredentialMode.DEFAULT_CHAIN);
    }

    @Test
    void whitespaceOnlyCredentialsAreEmpty() {
        assertThat(AliyunCredentialResolver.resolve("  LTAItestkey01  ", "  secretsecret  ", "  eci-role  "))
                .isEqualTo(AliyunCredentialMode.STATIC_AK);
        assertThat(AliyunCredentialResolver.resolve("   ", "   ", "  eci-role  "))
                .isEqualTo(AliyunCredentialMode.INSTANCE_PROFILE);
    }
}
