package com.xjicloud.eci.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.eci.common.BusinessException;
import com.xjicloud.eci.config.OssProperties;
import com.xjicloud.eci.oss.OssStorageService;
import com.xjicloud.eci.task.storage.TaskStorageRequest;
import com.xjicloud.eci.task.storage.TaskStorageResolver;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class TaskStorageResolverTest {

    private static final UUID TASK_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Test
    void rejectsStorageWhenOssDisabled() {
        TaskStorageResolver resolver = resolver(disabledOss());
        assertThatThrownBy(() -> resolver.resolveAndBuildEnv(TASK_ID, testMode(), Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OSS")
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void rejectsWhenOssIncomplete() {
        OssProperties props = new OssProperties();
        props.setEnabled(true);
        props.setEndpoint("https://oss-cn-hangzhou.aliyuncs.com");
        props.setAccessKey("ak");
        props.setSecretKey("sk");
        // bucket empty → not configured
        TaskStorageResolver resolver = resolver(props);
        assertThatThrownBy(() -> resolver.resolveAndBuildEnv(TASK_ID, testMode(), Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OSS");
    }

    @Test
    void allowsStorageWhenRuntimeKeysAppliedWithoutPropertiesAccessKey() {
        OssProperties props = new OssProperties();
        props.setEnabled(true);
        props.setEndpoint("https://oss-cn-hangzhou.aliyuncs.com");
        props.setRegion("cn-hangzhou");
        props.setBucket("test-bucket");
        props.setPresignExpirationMinutes(60);
        OssStorageService oss = new OssStorageService(props);
        oss.applyRuntimeKeys("runtime-access-key", "runtime-secret-key");
        TaskStorageResolver resolver = new TaskStorageResolver(props, oss, new ObjectMapper());

        Map<String, String> env = resolver.resolveAndBuildEnv(TASK_ID, testMode(), Map.of());

        assertThat(env.get("XJI_OUTPUT_OSS_KEY")).isEqualTo("eci-test/" + TASK_ID + "/output.txt");
        assertThat(env.get("XJI_OUTPUT_UPLOAD_URL")).isNotBlank();
        assertThat(env.get("XJI_OSS_BUCKET")).isEqualTo("test-bucket");
    }

    @Test
    void rejectsStorageWhenEnabledButLiveClientNotReady() {
        OssProperties props = new OssProperties();
        props.setEnabled(true);
        props.setEndpoint("https://oss-cn-hangzhou.aliyuncs.com");
        props.setBucket("test-bucket");
        TaskStorageResolver resolver = new TaskStorageResolver(
                props, new OssStorageService(props), new ObjectMapper());

        assertThatThrownBy(() -> resolver.resolveAndBuildEnv(TASK_ID, testMode(), Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OSS");
    }

    @Test
    void testModeBuildsEciTestPrefix() {
        TaskStorageResolver resolver = resolver(configuredOss());
        Map<String, String> env = resolver.resolveAndBuildEnv(TASK_ID, testMode(), Map.of());
        assertThat(env.get("XJI_OUTPUT_OSS_KEY"))
                .isEqualTo("eci-test/" + TASK_ID + "/output.txt");
        assertThat(env.get("XJI_OUTPUT_UPLOAD_URL")).isNotBlank();
        assertThat(env.get("XJI_OSS_ENDPOINT")).isEqualTo("https://oss-cn-hangzhou.aliyuncs.com");
        assertThat(env.get("XJI_OSS_BUCKET")).isEqualTo("test-bucket");
        assertThat(env.get("XJI_OSS_REGION")).isEqualTo("cn-hangzhou");
        assertThat(env.get("XJI_TASK_ID")).isEqualTo(TASK_ID.toString());
        assertThat(env).doesNotContainKey("XJI_INPUT_FILES_JSON");
    }

    @Test
    void keysModeWithInputKeysBuildsInputFilesJson() {
        TaskStorageResolver resolver = resolver(configuredOss());
        TaskStorageRequest storage = new TaskStorageRequest();
        storage.setMode("keys");
        storage.setOutputKey("outputs/job/result.bin");
        storage.setInputKeys(List.of("datasets/job/images/001.jpg"));

        Map<String, String> env = resolver.resolveAndBuildEnv(TASK_ID, storage, Map.of());
        assertThat(env.get("XJI_OUTPUT_OSS_KEY")).isEqualTo("outputs/job/result.bin");
        assertThat(env.get("XJI_OUTPUT_UPLOAD_URL")).isNotBlank();
        String json = env.get("XJI_INPUT_FILES_JSON");
        assertThat(json).isNotBlank();
        assertThat(json).contains("datasets/job/images/001.jpg");
        assertThat(json).contains("001.jpg");
        assertThat(json).contains("downloadUrl");
        assertThat(json).contains("https://");
    }

    @Test
    void rejectsDatasetMode() {
        TaskStorageResolver resolver = resolver(configuredOss());
        TaskStorageRequest storage = new TaskStorageRequest();
        storage.setMode("dataset");
        storage.setJobId(TASK_ID.toString());
        assertThatThrownBy(() -> resolver.resolveAndBuildEnv(TASK_ID, storage, Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("dataset");
    }

    @Test
    void rejectsKeysWithoutOutputKey() {
        TaskStorageResolver resolver = resolver(configuredOss());
        TaskStorageRequest storage = new TaskStorageRequest();
        storage.setMode("keys");
        assertThatThrownBy(() -> resolver.resolveAndBuildEnv(TASK_ID, storage, Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("outputKey");
    }

    @Test
    void callerEnvOverridesOssEnv() {
        TaskStorageResolver resolver = resolver(configuredOss());
        Map<String, String> env = resolver.resolveAndBuildEnv(
                TASK_ID, testMode(), Map.of("XJI_TASK_ID", "override"));
        assertThat(env.get("XJI_TASK_ID")).isEqualTo("override");
    }

    @Test
    void testModeBlankOutputFileNameDefaultsToOutputTxt() {
        TaskStorageRequest storage = new TaskStorageRequest();
        storage.setMode("test");
        storage.setOutputFileName("  ");
        Map<String, String> env = resolver(configuredOss())
                .resolveAndBuildEnv(TASK_ID, storage, null);
        assertThat(env.get("XJI_OUTPUT_OSS_KEY"))
                .isEqualTo("eci-test/" + TASK_ID + "/output.txt");
    }

    @Test
    void testModeCustomOutputFileName() {
        TaskStorageRequest storage = new TaskStorageRequest();
        storage.setMode("test");
        storage.setOutputFileName("result.log");
        Map<String, String> env = resolver(configuredOss())
                .resolveAndBuildEnv(TASK_ID, storage, null);
        assertThat(env.get("XJI_OUTPUT_OSS_KEY"))
                .isEqualTo("eci-test/" + TASK_ID + "/result.log");
    }

    @Test
    void modeIsCaseInsensitive() {
        TaskStorageRequest storage = new TaskStorageRequest();
        storage.setMode("TEST");
        Map<String, String> env = resolver(configuredOss())
                .resolveAndBuildEnv(TASK_ID, storage, Map.of());
        assertThat(env.get("XJI_OUTPUT_OSS_KEY"))
                .isEqualTo("eci-test/" + TASK_ID + "/output.txt");
    }

    @Test
    void rejectsBlankMode() {
        TaskStorageRequest storage = new TaskStorageRequest();
        storage.setMode("   ");
        assertThatThrownBy(() -> resolver(configuredOss())
                .resolveAndBuildEnv(TASK_ID, storage, Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("mode");
    }

    @Test
    void inputFilesJsonHasExactWorkerImageItemShape() throws Exception {
        TaskStorageResolver resolver = resolver(configuredOss());
        TaskStorageRequest storage = new TaskStorageRequest();
        storage.setMode("keys");
        storage.setOutputKey("outputs/job/result.bin");
        storage.setInputKeys(List.of(
                "datasets/job/images/001.jpg",
                "plain-key",
                "folder/"));

        Map<String, String> env = resolver.resolveAndBuildEnv(TASK_ID, storage, Map.of());
        var nodes = new ObjectMapper().readTree(env.get("XJI_INPUT_FILES_JSON"));
        assertThat(nodes).hasSize(3);
        assertThat(nodes.get(0).get("fileName").asText()).isEqualTo("001.jpg");
        assertThat(nodes.get(0).get("ossKey").asText()).isEqualTo("datasets/job/images/001.jpg");
        assertThat(nodes.get(0).get("downloadUrl").asText()).startsWith("https://");
        assertThat(nodes.get(1).get("fileName").asText()).isEqualTo("plain-key");
        assertThat(nodes.get(1).get("ossKey").asText()).isEqualTo("plain-key");
        assertThat(nodes.get(2).get("fileName").asText()).isEqualTo("folder/");
        assertThat(nodes.get(2).get("ossKey").asText()).isEqualTo("folder/");
    }

    private static TaskStorageRequest testMode() {
        TaskStorageRequest storage = new TaskStorageRequest();
        storage.setMode("test");
        return storage;
    }

    private static OssProperties configuredOss() {
        OssProperties props = new OssProperties();
        props.setEnabled(true);
        props.setEndpoint("https://oss-cn-hangzhou.aliyuncs.com");
        props.setRegion("cn-hangzhou");
        props.setBucket("test-bucket");
        props.setAccessKey("test-access-key");
        props.setSecretKey("test-secret-key");
        props.setPresignExpirationMinutes(60);
        return props;
    }

    private static OssProperties disabledOss() {
        OssProperties props = configuredOss();
        props.setEnabled(false);
        return props;
    }

    private static TaskStorageResolver resolver(OssProperties props) {
        OssStorageService oss = new OssStorageService(props);
        oss.init();
        return new TaskStorageResolver(props, oss, new ObjectMapper());
    }
}
