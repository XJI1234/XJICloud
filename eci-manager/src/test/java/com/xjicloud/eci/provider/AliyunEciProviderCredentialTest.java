package com.xjicloud.eci.provider;

import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.credential.AccessKeyPair;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AliyunEciProviderCredentialTest {

    @Test
    void reloadAccessKeyRejectsIncompletePairBeforeSdk() {
        AliyunEciProvider provider = new AliyunEciProvider(staticAkProperties());

        assertThatThrownBy(() -> provider.reloadAccessKey("LTAItestkey01", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("成对");
        assertThatThrownBy(() -> provider.reloadAccessKey("LTAItestkey01", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("成对");
        assertThatThrownBy(() -> provider.reloadAccessKey("", "secretsecret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("成对");
    }

    @Test
    void runtimePairBuildsStaticClientWhenPropertiesKeysEmpty() {
        EciProperties properties = staticAkProperties();
        properties.setAccessKeyId("");
        properties.setAccessKeySecret("");

        AliyunEciProvider provider = new AliyunEciProvider(
                properties, new AccessKeyPair("LTAItestkey01", "fake-secret-value"));

        assertThat(provider).isNotNull();
        assertThatCode(() -> provider.reloadAccessKey("LTAItestkey02", "fake-secret-value-2"))
                .doesNotThrowAnyException();
    }

    private static EciProperties staticAkProperties() {
        EciProperties properties = new EciProperties();
        properties.setMock(false);
        properties.setRegionId("cn-hangzhou");
        properties.setVswitchId("vsw-test");
        properties.setSecurityGroupId("sg-test");
        properties.setAccessKeyId("LTAItestkey01");
        properties.setAccessKeySecret("fake-secret-value");
        return properties;
    }
}
