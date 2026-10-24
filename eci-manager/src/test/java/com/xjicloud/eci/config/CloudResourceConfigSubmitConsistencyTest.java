package com.xjicloud.eci.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.eci.instance.EciInstanceEntity;
import com.xjicloud.eci.instance.EciInstanceRepository;
import com.xjicloud.eci.instance.EciInstanceService;
import com.xjicloud.eci.instance.dto.CreateInstanceRequest;
import com.xjicloud.eci.task.EciTaskEntity;
import com.xjicloud.eci.task.EciTaskRepository;
import com.xjicloud.eci.task.EciTaskService;
import com.xjicloud.eci.task.dto.CreateTaskRequest;
import com.xjicloud.eci.task.dto.TaskResponse;
import com.xjicloud.eci.task.storage.TaskStorageRequest;
import com.xjicloud.eci.oss.OssStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.poll-interval-ms=60000",
        "eci.mock-auto-complete-sec=0",
        "ECI_OSS_ENABLED=true",
        "ECI_OSS_ENDPOINT=https://oss-cn-hangzhou-internal.aliyuncs.com",
        "ECI_OSS_REGION=cn-hangzhou",
        "ECI_OSS_BUCKET=env-bucket",
        "ECI_OSS_PATH_STYLE_ACCESS=false",
        "ECI_OSS_PRESIGN_EXPIRATION_MINUTES=60",
        "eci.oss.access-key=test-submit-consistency-key",
        "eci.oss.secret-key=test-submit-consistency-secret",
        "spring.datasource.url=jdbc:h2:mem:eci-submit-config-consistency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class CloudResourceConfigSubmitConsistencyTest {

    private static final String API_KEY = "test-api-key";
    private static final String ACCESS_KEY = "test-submit-consistency-key";
    private static final String SECRET_KEY = "test-submit-consistency-secret";
    private static final String OUTPUT_KEY = "outputs/config-consistency/result.bin";
    private static final String INPUT_KEY = "inputs/config-consistency/source.bin";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CloudResourceConfigService configService;

    @Autowired
    private EciTaskService taskService;

    @Autowired
    private EciTaskRepository taskRepository;

    @Autowired
    private EciInstanceRepository instanceRepository;

    @Autowired
    private EciInstanceService instanceService;

    @SpyBean
    private EciProperties properties;

    @Autowired
    private ObjectMapper objectMapper;

    @SpyBean
    private OssStorageService ossStorageService;

    @Test
    void submissionKeepsNetworkAndOssSnapshotWhileConfigurationIsSaved() throws Exception {
        putCredential();
        ExpectedConfig oldConfig = new ExpectedConfig(
                "cn-hangzhou", "vpc-old", "vswitch-old", "security-group-old",
                "https://oss-cn-hangzhou-internal.aliyuncs.com", "cn-hangzhou", "old-bucket", 15);
        ExpectedConfig newConfig = new ExpectedConfig(
                "cn-shanghai", "vpc-new", "vswitch-new", "security-group-new",
                "https://oss-cn-shanghai-internal.aliyuncs.com", "cn-shanghai", "new-bucket", 45);
        saveNetwork(oldConfig);
        saveOss(oldConfig);

        CountDownLatch putSigned = new CountDownLatch(1);
        CountDownLatch releaseSubmission = new CountDownLatch(1);
        CountDownLatch saveStarted = new CountDownLatch(1);
        AtomicBoolean pauseFirstPut = new AtomicBoolean(true);
        doAnswer(invocation -> {
            String url = (String) invocation.callRealMethod();
            if (pauseFirstPut.compareAndSet(true, false)) {
                putSigned.countDown();
                awaitRelease(releaseSubmission);
            }
            return url;
        }).when(ossStorageService).presignPutUrl(anyString(), anyString());

        List<Running<?>> calls = new ArrayList<>();
        try {
            Running<TaskResponse> submission = start(
                    "task-submitter", calls, () -> taskService.create(taskRequest()));
            assertThat(putSigned.await(10, SECONDS)).as("PUT URL signed under submission snapshot").isTrue();

            Running<Void> configSave = start("config-writer", calls, () -> {
                saveStarted.countDown();
                saveNetwork(newConfig);
                saveOss(newConfig);
                return null;
            });
            assertThat(saveStarted.await(10, SECONDS)).as("configuration save started").isTrue();
            assertBlockedBy(configSave, submission);

            releaseSubmission.countDown();
            TaskResponse oldTask = submission.result();
            configSave.result();

            assertSubmittedSnapshot(oldTask.id(), oldConfig);
            TaskResponse newTask = taskService.create(taskRequest());
            assertSubmittedSnapshot(newTask.id(), newConfig);
        } finally {
            releaseSubmission.countDown();
            joinAll(calls);
        }
    }

    @Test
    void directCreateKeepsOneNetworkAndAdvancedSnapshotDuringConfigurationSwitch() {
        CloudResourceConfigSnapshot original = configService.snapshot();
        Map<String, String> oldNetwork = Map.of(
                "ECI_REGION_ID", "cn-hangzhou", "ECI_VPC_ID", "vpc-direct-old",
                "ECI_VSWITCH_ID", "vsw-direct-old", "ECI_SECURITY_GROUP_ID", "sg-direct-old");
        Map<String, String> newNetwork = Map.of(
                "ECI_REGION_ID", "cn-shanghai", "ECI_VPC_ID", "vpc-direct-new",
                "ECI_VSWITCH_ID", "vsw-direct-new", "ECI_SECURITY_GROUP_ID", "sg-direct-new");
        Map<String, String> oldAdvanced = Map.of(
                "ECI_DEFAULT_IMAGE", "direct-old:image", "ECI_DEFAULT_SHM_SIZE", "256Mi",
                "ECI_CREATE_TIMEOUT_SEC", "12");
        Map<String, String> newAdvanced = Map.of(
                "ECI_DEFAULT_IMAGE", "direct-new:image", "ECI_DEFAULT_SHM_SIZE", "512Mi",
                "ECI_CREATE_TIMEOUT_SEC", "120");
        AtomicBoolean switchOnce = new AtomicBoolean(true);
        try {
            configService.saveNetwork(oldNetwork);
            configService.saveAdvanced(oldAdvanced);
            // 在读取旧地域后发布新配置，暴露逐字段读取导致的混合快照。
            doAnswer(invocation -> {
                String region = (String) invocation.callRealMethod();
                if (switchOnce.compareAndSet(true, false)) {
                    configService.saveNetwork(newNetwork);
                    configService.saveAdvanced(newAdvanced);
                }
                return region;
            }).when(properties).getRegionId();

            UUID firstId = instanceService.create(new CreateInstanceRequest()).id();
            configService.saveNetwork(newNetwork);
            configService.saveAdvanced(newAdvanced);
            UUID nextId = instanceService.create(new CreateInstanceRequest()).id();

            assertThat(instanceRepository.findById(firstId).orElseThrow())
                    .extracting(EciInstanceEntity::getRegionId, EciInstanceEntity::getVpcId,
                            EciInstanceEntity::getVswitchId, EciInstanceEntity::getSecurityGroupId,
                            EciInstanceEntity::getImage, EciInstanceEntity::getShmSize,
                            EciInstanceEntity::getCreateTimeoutSec)
                    .containsExactly("cn-hangzhou", "vpc-direct-old", "vsw-direct-old", "sg-direct-old",
                            "direct-old:image", "256Mi", 12);
            assertThat(instanceRepository.findById(nextId).orElseThrow())
                    .extracting(EciInstanceEntity::getRegionId, EciInstanceEntity::getVpcId,
                            EciInstanceEntity::getVswitchId, EciInstanceEntity::getSecurityGroupId,
                            EciInstanceEntity::getImage, EciInstanceEntity::getShmSize,
                            EciInstanceEntity::getCreateTimeoutSec)
                    .containsExactly("cn-shanghai", "vpc-direct-new", "vsw-direct-new", "sg-direct-new",
                            "direct-new:image", "512Mi", 120);
        } finally {
            switchOnce.set(false);
            configService.saveNetwork(Map.of(
                    "ECI_REGION_ID", original.network().regionId(), "ECI_VPC_ID", original.network().vpcId(),
                    "ECI_VSWITCH_ID", original.network().vswitchId(),
                    "ECI_SECURITY_GROUP_ID", original.network().securityGroupId()));
            configService.saveAdvanced(Map.of(
                    "ECI_DEFAULT_IMAGE", original.advanced().defaultImage(),
                    "ECI_DEFAULT_SHM_SIZE", original.advanced().defaultShmSize(),
                    "ECI_CREATE_TIMEOUT_SEC", Integer.toString(original.advanced().createTimeoutSec())));
        }
    }

    private void putCredential() throws Exception {
        mockMvc.perform(put("/api/v1/eci/runtime-credentials").header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"OSS\",\"accessKeyId\":\"" + ACCESS_KEY
                                + "\",\"accessKeySecret\":\"" + SECRET_KEY + "\"}"))
                .andExpect(status().isOk());
    }

    private void saveNetwork(ExpectedConfig expected) {
        configService.saveNetwork(Map.of(
                "ECI_REGION_ID", expected.regionId(),
                "ECI_VPC_ID", expected.vpcId(),
                "ECI_VSWITCH_ID", expected.vswitchId(),
                "ECI_SECURITY_GROUP_ID", expected.securityGroupId()));
    }

    private void saveOss(ExpectedConfig expected) {
        configService.saveOss(Map.of(
                "ECI_OSS_ENABLED", "true",
                "ECI_OSS_ENDPOINT", expected.endpoint(),
                "ECI_OSS_REGION", expected.ossRegion(),
                "ECI_OSS_BUCKET", expected.bucket(),
                "ECI_OSS_PATH_STYLE_ACCESS", "false",
                "ECI_OSS_PRESIGN_EXPIRATION_MINUTES", Integer.toString(expected.expirationMinutes())));
    }

    private void assertSubmittedSnapshot(UUID taskId, ExpectedConfig expected) throws Exception {
        EciTaskEntity task = taskRepository.findById(taskId).orElseThrow();
        EciInstanceEntity instance = instanceRepository.findAll().stream()
                .filter(candidate -> taskId.equals(candidate.getTaskId()))
                .findFirst()
                .orElseThrow();
        assertThat(instance.getRegionId()).isEqualTo(expected.regionId());
        assertThat(instance.getVpcId()).isEqualTo(expected.vpcId());
        assertThat(instance.getVswitchId()).isEqualTo(expected.vswitchId());
        assertThat(instance.getSecurityGroupId()).isEqualTo(expected.securityGroupId());

        Map<String, String> env = objectMapper.readValue(task.getEnvJson(), new TypeReference<>() {
        });
        assertThat(env.get("XJI_OSS_ENDPOINT")).isEqualTo(expected.endpoint());
        assertThat(env.get("XJI_OSS_REGION")).isEqualTo(expected.ossRegion());
        assertThat(env.get("XJI_OSS_BUCKET")).isEqualTo(expected.bucket());
        assertPresignedUrl(env.get("XJI_OUTPUT_UPLOAD_URL"), expected, OUTPUT_KEY);

        JsonNode inputFiles = objectMapper.readTree(env.get("XJI_INPUT_FILES_JSON"));
        assertThat(inputFiles).hasSize(1);
        assertThat(inputFiles.get(0).get("ossKey").asText()).isEqualTo(INPUT_KEY);
        assertPresignedUrl(inputFiles.get(0).get("downloadUrl").asText(), expected, INPUT_KEY);
    }

    private static void assertPresignedUrl(String value, ExpectedConfig expected, String key) {
        URI uri = URI.create(value);
        assertThat(uri.getHost()).isEqualTo(expected.bucket() + "." + URI.create(expected.endpoint()).getHost());
        assertThat(uri.getPath()).isEqualTo("/" + key);
        assertThat(URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8))
                .contains("X-Amz-Credential=" + ACCESS_KEY + "/")
                .contains("X-Amz-Expires=" + expected.expirationMinutes() * 60 + "&");
    }

    private static CreateTaskRequest taskRequest() {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setImage("nginx:alpine");
        TaskStorageRequest storage = new TaskStorageRequest();
        storage.setMode("keys");
        storage.setOutputKey(OUTPUT_KEY);
        storage.setInputKeys(List.of(INPUT_KEY));
        request.setStorage(storage);
        return request;
    }

    private static void awaitRelease(CountDownLatch release) throws InterruptedException {
        assertThat(release.await(30, SECONDS)).as("test releases the paused submission").isTrue();
    }

    private static void assertBlockedBy(Running<?> waiting, Running<?> owner) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ThreadInfo info = ManagementFactory.getThreadMXBean().getThreadInfo(waiting.thread().getId());
            assertThat(info).as(waiting.thread().getName() + " is alive").isNotNull();
            assertThat(info.getThreadState()).isEqualTo(Thread.State.BLOCKED);
            assertThat(info.getLockOwnerId()).isEqualTo(owner.thread().getId());
            assertThat(waiting.task().isDone()).isFalse();
        });
    }

    private static <T> Running<T> start(String name, List<Running<?>> calls, Callable<T> action) {
        FutureTask<T> task = new FutureTask<>(action);
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        Running<T> running = new Running<>(thread, task);
        calls.add(running);
        thread.start();
        return running;
    }

    private static void joinAll(List<Running<?>> calls) throws InterruptedException {
        for (Running<?> call : calls) {
            call.thread().join(10_000);
            assertThat(call.thread().isAlive()).as(call.thread().getName() + " terminated").isFalse();
        }
    }

    private record ExpectedConfig(
            String regionId,
            String vpcId,
            String vswitchId,
            String securityGroupId,
            String endpoint,
            String ossRegion,
            String bucket,
            int expirationMinutes) {
    }

    private record Running<T>(Thread thread, FutureTask<T> task) {
        T result() throws Exception {
            return task.get(10, SECONDS);
        }
    }
}
