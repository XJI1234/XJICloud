package com.xjicloud.eci.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.eci.instance.EciInstanceEntity;
import com.xjicloud.eci.instance.EciInstanceRepository;
import com.xjicloud.eci.instance.InstanceStatus;
import com.xjicloud.eci.provider.EciProvider;
import com.xjicloud.eci.provider.MockEciProvider;
import com.xjicloud.eci.task.dto.TaskResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /tasks/{id}/logs：鉴权、无 cloudId 空日志、Mock 假日志、终态走 containerLogTail。
 */
@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.poll-interval-ms=200",
        "eci.mock-auto-complete-sec=0",
        "eci.console.open-browser=false",
        "spring.datasource.url=jdbc:h2:mem:eci-task-logs;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class EciTaskLogsApiTest {

    private static final String API_KEY = "test-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EciTaskRepository taskRepository;

    @Autowired
    private EciInstanceRepository instanceRepository;

    @Autowired
    private EciProvider eciProvider;

    @Test
    void rejectsMissingApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/eci/tasks/" + UUID.randomUUID() + "/logs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownTaskReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/eci/tasks/" + UUID.randomUUID() + "/logs")
                        .header("X-Api-Key", API_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("任务不存在")));
    }

    @Test
    void rejectsNonPositiveTailLines() throws Exception {
        UUID taskId = persistStartingTaskWithoutCloudId().taskId;
        mockMvc.perform(get("/api/v1/eci/tasks/" + taskId + "/logs?tailLines=0")
                        .header("X-Api-Key", API_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("tailLines")));
    }

    @Test
    void noCloudIdYetReturnsEmpty200() throws Exception {
        Ids ids = persistStartingTaskWithoutCloudId();
        mockMvc.perform(get("/api/v1/eci/tasks/" + ids.taskId + "/logs")
                        .header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.taskId").value(ids.taskId.toString()))
                .andExpect(jsonPath("$.data.source").value("EMPTY"))
                .andExpect(jsonPath("$.data.content").value(""))
                .andExpect(jsonPath("$.data.tailLines").value(200));
    }

    @Test
    void runningTaskReturnsMockLiveLog() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/eci/tasks")
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"image\":\"nginx:alpine\",\"restartPolicy\":\"Never\",\"maxRuntimeSec\":60}"))
                .andExpect(status().isOk())
                .andReturn();
        String taskId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();
        String instanceId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("instanceId").asText();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            MvcResult getResult = mockMvc.perform(get("/api/v1/eci/tasks/" + taskId)
                            .header("X-Api-Key", API_KEY))
                    .andExpect(status().isOk())
                    .andReturn();
            TaskResponse body = objectMapper.treeToValue(
                    objectMapper.readTree(getResult.getResponse().getContentAsString()).path("data"),
                    TaskResponse.class);
            assertThat(body.status()).isEqualTo(TaskStatus.RUNNING);
        });

        String cloudId = objectMapper.readTree(mockMvc.perform(get("/api/v1/eci/instances/" + instanceId)
                        .header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
                .path("data").path("cloudInstanceId").asText();

        mockMvc.perform(get("/api/v1/eci/tasks/" + taskId + "/logs?tailLines=50")
                        .header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.source").value("LIVE"))
                .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.containsString("mock-container-log")))
                .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.containsString(cloudId)))
                .andExpect(jsonPath("$.data.tailLines").value(50));
    }

    @Test
    void terminalTaskReturnsPersistedTailWithoutLiveFetch() throws Exception {
        Ids ids = persistFailedTaskWithTail("persisted-tail-abc");
        MockEciProvider mock = (MockEciProvider) eciProvider;
        int fetchesBefore = mock.getFetchContainerLogCalls();

        mockMvc.perform(get("/api/v1/eci/tasks/" + ids.taskId + "/logs")
                        .header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("PERSISTED"))
                .andExpect(jsonPath("$.data.content").value("persisted-tail-abc"))
                .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("mock-container-log"))));

        assertThat(mock.getFetchContainerLogCalls()).isEqualTo(fetchesBefore);
    }

    @Test
    void succeededEmptyTailExplainsNoFullLog() throws Exception {
        Ids ids = persistSucceededTask();
        mockMvc.perform(get("/api/v1/eci/tasks/" + ids.taskId + "/logs")
                        .header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("PERSISTED"))
                .andExpect(jsonPath("$.data.content").value(""))
                .andExpect(jsonPath("$.data.note").value(org.hamcrest.Matchers.containsString("成功任务不保留全量日志")));
    }

    @Test
    void fetchFailureReturnsReadableString() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/eci/tasks")
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"image\":\"nginx:alpine\",\"restartPolicy\":\"Never\",\"maxRuntimeSec\":60}"))
                .andExpect(status().isOk())
                .andReturn();
        String taskId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();
        String instanceId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("instanceId").asText();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/eci/tasks/" + taskId)
                                .header("X-Api-Key", API_KEY))
                        .andExpect(jsonPath("$.data.status").value("RUNNING")));

        String cloudId = objectMapper.readTree(mockMvc.perform(get("/api/v1/eci/instances/" + instanceId)
                        .header("X-Api-Key", API_KEY))
                .andReturn()
                .getResponse()
                .getContentAsString())
                .path("data").path("cloudInstanceId").asText();

        ((MockEciProvider) eciProvider).failNextFetch(cloudId, "log gone");

        mockMvc.perform(get("/api/v1/eci/tasks/" + taskId + "/logs")
                        .header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.source").value("LIVE"))
                .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.containsString("fetchContainerLog failed")))
                .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.containsString("log gone")));
    }

    private Ids persistStartingTaskWithoutCloudId() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        Instant now = Instant.now();

        EciInstanceEntity inst = new EciInstanceEntity();
        inst.setId(instanceId);
        inst.setName("pending-inst");
        inst.setImage("nginx:alpine");
        inst.setCpu(1);
        inst.setMemory(2);
        inst.setStatus(InstanceStatus.CREATING);
        inst.setTaskId(taskId);
        inst.setCreatedAt(now);
        inst.setUpdatedAt(now);
        instanceRepository.save(inst);

        EciTaskEntity task = baseTask(taskId, instanceId, now);
        task.setStatus(TaskStatus.STARTING);
        taskRepository.save(task);
        return new Ids(taskId, instanceId);
    }

    private Ids persistFailedTaskWithTail(String tail) {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        Instant now = Instant.now();

        EciInstanceEntity inst = new EciInstanceEntity();
        inst.setId(instanceId);
        inst.setName("failed-inst");
        inst.setImage("nginx:alpine");
        inst.setCpu(1);
        inst.setMemory(2);
        inst.setStatus(InstanceStatus.TERMINATED);
        inst.setCloudInstanceId("mock-eci-already-gone");
        inst.setTaskId(taskId);
        inst.setCreatedAt(now);
        inst.setUpdatedAt(now);
        instanceRepository.save(inst);

        EciTaskEntity task = baseTask(taskId, instanceId, now);
        task.setStatus(TaskStatus.FAILED);
        task.setFailureReason("cloud Failed");
        task.setContainerLogTail(tail);
        task.setEndedAt(now);
        taskRepository.save(task);
        return new Ids(taskId, instanceId);
    }

    private Ids persistSucceededTask() {
        UUID taskId = UUID.randomUUID();
        Instant now = Instant.now();
        EciTaskEntity task = baseTask(taskId, null, now);
        task.setStatus(TaskStatus.SUCCEEDED);
        task.setEndedAt(now);
        taskRepository.save(task);
        return new Ids(taskId, null);
    }

    private static EciTaskEntity baseTask(UUID taskId, UUID instanceId, Instant now) {
        EciTaskEntity task = new EciTaskEntity();
        task.setId(taskId);
        task.setName("log-task");
        task.setInstanceId(instanceId);
        task.setImage("nginx:alpine");
        task.setCpu(1);
        task.setMemory(2);
        task.setRestartPolicy("Never");
        task.setMaxRuntimeSec(60);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }

    private record Ids(UUID taskId, UUID instanceId) {
    }
}
