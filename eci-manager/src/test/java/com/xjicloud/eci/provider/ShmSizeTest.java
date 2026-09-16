package com.xjicloud.eci.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShmSizeTest {

    @Test
    void normalizeAliyunAndDockerUnits() {
        assertThat(ShmSize.normalize("256Mi")).isEqualTo("256Mi");
        assertThat(ShmSize.normalize("8Gi")).isEqualTo("8Gi");
        assertThat(ShmSize.normalize("8G")).isEqualTo("8Gi");
        assertThat(ShmSize.normalize("256M")).isEqualTo("256Mi");
        assertThat(ShmSize.normalize(" 128mi ")).isEqualTo("128Mi");
    }

    @Test
    void normalizeRejectsBlankMissingUnitAndZero() {
        assertThatThrownBy(() -> ShmSize.normalize(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
        assertThatThrownBy(() -> ShmSize.normalize("8"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("带单位");
        assertThatThrownBy(() -> ShmSize.normalize("8GB"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ShmSize.normalize("0Gi"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("正数");
    }

    @Test
    void resolveBlankKeepsDefaultShm() {
        assertThat(ShmSize.resolve(null, "", 2.0)).isNull();
        assertThat(ShmSize.resolve("  ", null, 2.0)).isNull();
    }

    @Test
    void resolvePrefersRequestOverDefaultAndFitsMemory() {
        assertThat(ShmSize.resolve("8G", "256Mi", 60.0)).isEqualTo("8Gi");
        assertThat(ShmSize.resolve(null, "256Mi", 2.0)).isEqualTo("256Mi");
    }

    @Test
    void resolveRejectsDefaultKubernetesSizeAndOversized() {
        assertThatThrownBy(() -> ShmSize.resolve("64Mi", null, 2.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("大于 64Mi");
        assertThatThrownBy(() -> ShmSize.resolve("8Gi", null, 2.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("严格小于");
        assertThatThrownBy(() -> ShmSize.resolve("2Gi", null, 2.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("严格小于");
    }

    @Test
    void toBytesUsesBinaryUnits() {
        assertThat(ShmSize.toBytes("256Mi")).isEqualTo(256L * 1024 * 1024);
        assertThat(ShmSize.toBytes("8Gi")).isEqualTo(8L * 1024 * 1024 * 1024);
    }
}
