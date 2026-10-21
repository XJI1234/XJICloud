package com.xjicloud.eci.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.xjicloud.eci.instance.EciInstanceEntity;
import com.xjicloud.eci.instance.EciInstanceRepository;
import com.xjicloud.eci.task.dto.TaskResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mock 模式下任务 API 契约：鉴权、校验、提交、状态联动、cancel 幂等。
 */
@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.poll-interval-ms=200",
        "eci.mock-auto-complete-sec=0",
        "spring.datasource.url=jdbc:h2:mem:eci-task-api-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class EciTaskApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    private EciTaskRepository taskRepository;

    @Autowired
    private EciInstanceRepository instanceRepository;

    @Test
    void rejectsMissingApiKey() throws Exception {
        mockMvc.perform(post("/api/v1/eci/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"image\":\"nginx:alpine\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMissingImage() throws Exception {
        mockMvc.perform(post("/api/v1/eci/tasks")
                        .header("X-Api-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"no-image\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void submitQueryAndReachRunning() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/eci/tasks")
                        .header("X-Api-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"image\":\"nginx:alpine\",\"restartPolicy\":\"Never\",\"maxRuntimeSec\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("STARTING"))
                .andExpect(jsonPath("$.data.instanceId").isNotEmpty())
                .andExpect(jsonPath("$.data.image").value("nginx:alpine"))
                .andExpect(jsonPath("$.data.maxRuntimeSec").value(60))
                .andReturn();

        String taskId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();
        String instanceId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("instanceId").asText();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            MvcResult getResult = mockMvc.perform(get("/api/v1/eci/tasks/" + taskId)
                            .header("X-Api-Key", "test-api-key"))
                    .andExpect(status().isOk())
                    .andReturn();
            TaskResponse body = objectMapper.treeToValue(
                    objectMapper.readTree(getResult.getResponse().getContentAsString()).path("data"),
                    TaskResponse.class);
            assertThat(body.status()).isEqualTo(TaskStatus.RUNNING);
            assertThat(body.startedAt()).isNotNull();
        });

        mockMvc.perform(get("/api/v1/eci/instances/" + instanceId)
                        .header("X-Api-Key", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(taskId));

        mockMvc.perform(get("/api/v1/eci/tasks?status=RUNNING")
                        .header("X-Api-Key", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + taskId + "')]").exists());
    }

    @Test
    void submitTaskWithImageSnapshotId() throws Exception {
        mockMvc.perform(post("/api/v1/eci/tasks")
                        .header("X-Api-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"image\":\"nginx:alpine\",\"imageSnapshotId\":\"imc-test-snapshot-id\",\"restartPolicy\":\"Never\",\"maxRuntimeSec\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.imageSnapshotId").value("imc-test-snapshot-id"));
    }

    @Test
    void repeatedClientRequestIdReturnsTheSameTaskAndOneInstance() throws Exception {
        String clientRequestId = "repeat-" + UUID.randomUUID();
        long tasksBefore = taskRepository.count();
        long instancesBefore = instanceRepository.count();

        JsonNode first = submitTaskWithClientRequestId(clientRequestId);
        JsonNode second = submitTaskWithClientRequestId(clientRequestId);

        UUID taskId = UUID.fromString(first.path("data").path("id").asText());
        assertThat(second.path("data").path("id").asText())
                .isEqualTo(first.path("data").path("id").asText());
        assertThat(second.path("data").path("instanceId").asText())
                .isEqualTo(first.path("data").path("instanceId").asText());
        assertThat(taskRepository.count() - tasksBefore).isEqualTo(1);
        assertThat(instanceRepository.count() - instancesBefore).isEqualTo(1);
        assertThat(instancesForTask(taskId)).isEqualTo(1);
    }

    @Test
    void differentClientRequestIdsCreateDifferentTasksAndInstances() throws Exception {
        String firstKey = "distinct-a-" + UUID.randomUUID();
        String secondKey = "distinct-b-" + UUID.randomUUID();
        long tasksBefore = taskRepository.count();
        long instancesBefore = instanceRepository.count();

        JsonNode first = submitTaskWithClientRequestId(firstKey);
        JsonNode second = submitTaskWithClientRequestId(secondKey);

        assertThat(second.path("data").path("id").asText())
                .isNotEqualTo(first.path("data").path("id").asText());
        assertThat(second.path("data").path("instanceId").asText())
                .isNotEqualTo(first.path("data").path("instanceId").asText());
        assertThat(taskRepository.count() - tasksBefore).isEqualTo(2);
        assertThat(instanceRepository.count() - instancesBefore).isEqualTo(2);
    }

    @Test
    void concurrentRequestsWithTheSameClientRequestIdConverge() throws Exception {
        String clientRequestId = "concurrent-" + UUID.randomUUID();
        long tasksBefore = taskRepository.count();
        long instancesBefore = instanceRepository.count();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<JsonNode> firstFuture = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("timed out waiting to submit concurrent request");
                }
                return submitTaskWithClientRequestId(clientRequestId);
            });
            Future<JsonNode> secondFuture = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("timed out waiting to submit concurrent request");
                }
                return submitTaskWithClientRequestId(clientRequestId);
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            JsonNode first = firstFuture.get(15, TimeUnit.SECONDS);
            JsonNode second = secondFuture.get(15, TimeUnit.SECONDS);

            UUID taskId = UUID.fromString(first.path("data").path("id").asText());
            assertThat(second.path("data").path("id").asText())
                    .isEqualTo(first.path("data").path("id").asText());
            assertThat(second.path("data").path("instanceId").asText())
                    .isEqualTo(first.path("data").path("instanceId").asText());
            assertThat(taskRepository.count() - tasksBefore).isEqualTo(1);
            assertThat(instanceRepository.count() - instancesBefore).isEqualTo(1);
            assertThat(instancesForTask(taskId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void requestsWithoutClientRequestIdStillCreateSeparateTasks() throws Exception {
        long tasksBefore = taskRepository.count();
        long instancesBefore = instanceRepository.count();

        JsonNode first = submitTaskWithoutClientRequestId();
        JsonNode second = submitTaskWithoutClientRequestId();

        assertThat(second.path("data").path("id").asText())
                .isNotEqualTo(first.path("data").path("id").asText());
        assertThat(second.path("data").path("instanceId").asText())
                .isNotEqualTo(first.path("data").path("instanceId").asText());
        assertThat(taskRepository.count() - tasksBefore).isEqualTo(2);
        assertThat(instanceRepository.count() - instancesBefore).isEqualTo(2);
    }

    private JsonNode submitTaskWithClientRequestId(String clientRequestId) throws Exception {
        String body = objectMapper.createObjectNode()
                .put("image", "nginx:alpine")
                .put("clientRequestId", clientRequestId)
                .toString();
        return submitTask(body);
    }

    private JsonNode submitTaskWithoutClientRequestId() throws Exception {
        return submitTask("{\"image\":\"nginx:alpine\"}");
    }

    private JsonNode submitTask(String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/eci/tasks")
                        .header("X-Api-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private long instancesForTask(UUID taskId) {
        return instanceRepository.findAll().stream()
                .map(EciInstanceEntity::getTaskId)
                .filter(taskId::equals)
                .count();
    }

    @Test
    void submitTaskWithShmSizeEchoesCanonicalValue() throws Exception {
        mockMvc.perform(post("/api/v1/eci/tasks")
                        .header("X-Api-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"image\":\"nginx:alpine\",\"shmSize\":\"256M\",\"memory\":2.0,\"restartPolicy\":\"Never\",\"maxRuntimeSec\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shmSize").value("256Mi"));
    }

    @Test
    void submitTaskRejectsInvalidOrOversizedShmSizeWithoutPersisting() throws Exception {
        long before = taskRepository.count();

        mockMvc.perform(post("/api/v1/eci/tasks")
                        .header("X-Api-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"image\":\"nginx:alpine\",\"shmSize\":\"8Gi\",\"restartPolicy\":\"Never\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("严格小于")));

        mockMvc.perform(post("/api/v1/eci/tasks")
                        .header("X-Api-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"image\":\"nginx:alpine\",\"shmSize\":\"8\",\"restartPolicy\":\"Never\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("带单位")));

        assertThat(taskRepository.count()).isEqualTo(before);
    }

    @Test
    void submitTaskWithoutShmSizeLeavesFieldNull() throws Exception {
        mockMvc.perform(post("/api/v1/eci/tasks")
                        .header("X-Api-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"image\":\"nginx:alpine\",\"restartPolicy\":\"Never\",\"maxRuntimeSec\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shmSize").value(nullValue()));
    }

    @Test
    void cancelIsIdempotent() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/eci/tasks")
                        .header("X-Api-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"image\":\"nginx:alpine\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String taskId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(post("/api/v1/eci/tasks/" + taskId + "/cancel")
                        .header("X-Api-Key", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLING"))
                .andExpect(jsonPath("$.data.endedAt").value(nullValue()));

        mockMvc.perform(post("/api/v1/eci/tasks/" + taskId + "/cancel")
                        .header("X-Api-Key", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLING"));
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"SUCCEEDED"})
    void cancelRejectsSucceededTask(TaskStatus terminalStatus) throws Exception {
        UUID taskId = UUID.randomUUID();
        taskRepository.saveAndFlush(terminalTask(taskId, terminalStatus));

        mockMvc.perform(post("/api/v1/eci/tasks/" + taskId + "/cancel")
                        .header("X-Api-Key", "test-api-key"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    private EciTaskEntity terminalTask(UUID taskId, TaskStatus status) {
        EciTaskEntity task = new EciTaskEntity();
        task.setId(taskId);
        task.setName("terminal-task");
        task.setStatus(status);
        task.setImage("nginx:alpine");
        task.setCpu(1.0);
        task.setMemory(2.0);
        task.setRestartPolicy("Never");
        task.setMaxRuntimeSec(60);
        task.setEnvJson("{}");
        task.setEndedAt(Instant.now());
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        return task;
    }

    @Test
    void manualInstanceHasNullTaskId() throws Exception {
        mockMvc.perform(post("/api/v1/eci/instances")
                        .header("X-Api-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"manual-no-task\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.taskId").value(nullValue()));
    }
}
