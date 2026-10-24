package com.xjicloud.eci.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.config.OssProperties;
import com.xjicloud.eci.instance.EciInstanceRepository;
import com.xjicloud.eci.instance.EciInstanceService;
import com.xjicloud.eci.instance.InstanceStatus;
import com.xjicloud.eci.instance.dto.InstanceResponse;
import com.xjicloud.eci.oss.OssStorageService;
import com.xjicloud.eci.task.dto.CreateTaskRequest;
import com.xjicloud.eci.task.storage.TaskStorageRequest;
import com.xjicloud.eci.task.storage.TaskStorageResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 提交时快照：共享内存、失败取证与 OSS 解析结果落库后，配置变更只影响新任务。
 */
class EciTaskConfigSnapshotTest {

    private static final String OSS_ENDPOINT = "https://oss-cn-hangzhou.aliyuncs.com";
    private static final String OSS_ACCESS_KEY = "test-access-key";
    private static final String OSS_SECRET_KEY = "test-secret-key";

    private EciTaskRepository repository;
    private EciProperties properties;
    private OssProperties ossProperties;
    private OssStorageService ossStorageService;
    private EciTaskService service;

    @BeforeEach
    void setUp() {
        repository = mock(EciTaskRepository.class);
        EciInstanceService instanceService = mock(EciInstanceService.class);
        properties = new EciProperties();
        properties.setDefaultShmSize("256Mi");
        properties.setFailureLogTailLines(111);
        properties.setFailureLogGraceSec(22);
        ossProperties = new OssProperties();
        ossProperties.setEnabled(true);
        ossProperties.setEndpoint(OSS_ENDPOINT);
        ossProperties.setRegion("cn-hangzhou");
        ossProperties.setBucket("bucket-old");
        ossProperties.setAccessKey(OSS_ACCESS_KEY);
        ossProperties.setSecretKey(OSS_SECRET_KEY);
        ossProperties.setPresignExpirationMinutes(60);
        ossStorageService = new OssStorageService(ossProperties);
        ossStorageService.init();
        service = new EciTaskService(
                repository,
                mock(EciInstanceRepository.class),
                instanceService,
                properties,
                new ObjectMapper(),
                new TaskStorageResolver(ossProperties, ossStorageService, new ObjectMapper()),
                mock(PlatformTransactionManager.class));
        when(instanceService.createForTask(any(), any())).thenAnswer(invocation -> {
            UUID taskId = invocation.getArgument(1);
            Instant now = Instant.now();
            return new InstanceResponse(UUID.randomUUID(), taskId, "task", "nginx:alpine", 1.0, 2.0,
                    InstanceStatus.CREATING, null, null, Map.of(), null, now, now);
        });
    }

    @Test
    void submittedTaskKeepsStorageAndFailureForensicsSnapshotAfterConfigChange() throws Exception {
        service.create(requestWithStorage());
        ArgumentCaptor<EciTaskEntity> firstSaved = ArgumentCaptor.forClass(EciTaskEntity.class);
        verify(repository, times(2)).save(firstSaved.capture());

        EciTaskEntity firstTask = firstSaved.getValue();
        assertThat(firstTask.getShmSize()).isEqualTo("256Mi");
        assertThat(firstTask.getFailureLogTailLines()).isEqualTo(111);
        assertThat(firstTask.getFailureLogGraceSec()).isEqualTo(22);
        Map<String, String> firstEnv = envOf(firstTask);
        assertThat(firstEnv.get("XJI_OSS_BUCKET")).isEqualTo("bucket-old");
        String firstUploadUrl = firstEnv.get("XJI_OUTPUT_UPLOAD_URL");
        assertThat(firstUploadUrl).contains("bucket-old");

        // 保存新配置后：已提交任务的 env（含预签名 URL）不得被重算或重签
        properties.setDefaultShmSize("512Mi");
        properties.setFailureLogTailLines(999);
        properties.setFailureLogGraceSec(77);
        ossProperties.setBucket("bucket-new");
        ossStorageService.applyRuntime(ossStorageService.prepare(
                true, OSS_ENDPOINT, "cn-hangzhou", "bucket-new", false, 60,
                OSS_ACCESS_KEY, OSS_SECRET_KEY));

        assertThat(firstTask.getShmSize()).isEqualTo("256Mi");
        assertThat(firstTask.getFailureLogTailLines()).isEqualTo(111);
        assertThat(firstTask.getFailureLogGraceSec()).isEqualTo(22);
        assertThat(envOf(firstTask).get("XJI_OUTPUT_UPLOAD_URL")).isEqualTo(firstUploadUrl);

        // 同一配置只影响新任务
        service.create(requestWithStorage());
        ArgumentCaptor<EciTaskEntity> secondSaved = ArgumentCaptor.forClass(EciTaskEntity.class);
        verify(repository, times(4)).save(secondSaved.capture());

        EciTaskEntity secondTask = secondSaved.getValue();
        assertThat(secondTask.getShmSize()).isEqualTo("512Mi");
        assertThat(secondTask.getFailureLogTailLines()).isEqualTo(999);
        assertThat(secondTask.getFailureLogGraceSec()).isEqualTo(77);
        Map<String, String> secondEnv = envOf(secondTask);
        assertThat(secondEnv.get("XJI_OSS_BUCKET")).isEqualTo("bucket-new");
        assertThat(secondEnv.get("XJI_OUTPUT_UPLOAD_URL")).contains("bucket-new");
        assertThat(secondEnv.get("XJI_OUTPUT_UPLOAD_URL")).isNotEqualTo(firstUploadUrl);
    }

    private static CreateTaskRequest requestWithStorage() {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setImage("nginx:alpine");
        TaskStorageRequest storage = new TaskStorageRequest();
        storage.setMode("test");
        request.setStorage(storage);
        return request;
    }

    private static Map<String, String> envOf(EciTaskEntity entity) throws Exception {
        return new ObjectMapper().readValue(entity.getEnvJson(), new TypeReference<>() {
        });
    }
}
