package com.xjicloud.eci.lifecycle.action;

import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.config.CloudResourceConfigService;
import com.xjicloud.eci.instance.EciInstanceEntity;
import com.xjicloud.eci.instance.EciInstanceRepository;
import com.xjicloud.eci.instance.InstanceStatus;
import com.xjicloud.eci.provider.CloudInstanceView;
import com.xjicloud.eci.provider.EciProvider;
import com.xjicloud.eci.task.EciTaskEntity;
import com.xjicloud.eci.task.EciTaskRepository;
import com.xjicloud.eci.task.EciTaskService;
import com.xjicloud.eci.task.TaskStatus;
import com.xjicloud.eci.task.dto.CreateTaskRequest;
import com.xjicloud.eci.task.dto.TaskResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.lifecycle-action.dispatch-interval-ms=3600000",
        "spring.datasource.url=jdbc:h2:mem:eci-create-runtime-snapshot;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@Import(CreateRuntimeSnapshotRegressionTest.FixedClockConfiguration.class)
class CreateRuntimeSnapshotRegressionTest {

    private static final Instant NOW = Instant.parse("2026-10-24T00:00:00Z");
    private static final String REGION_ID = "cn-hangzhou";

    @SpyBean
    private EciInstanceRepository instanceRepository;
    @Autowired
    private LifecycleActionRepository actionRepository;
    @Autowired
    private LifecycleActionService actionService;
    @Autowired
    private LifecycleActionProcessor processor;
    @Autowired
    private EciProperties properties;
    @Autowired
    private CloudResourceConfigService configService;
    @Autowired
    private EciTaskRepository taskRepository;
    @Autowired
    private EciTaskService taskService;

    @MockBean
    private EciProvider provider;

    private int originalCreateTimeoutSec;
    private int originalFailureLogTailLines;
    private int originalFailureLogGraceSec;
    private final List<InstanceSave> instanceSaves = new ArrayList<>();

    @BeforeEach
    void rememberRuntimeSettings() {
        instanceSaves.clear();
        Answer<?> repositoryAnswer = mockingDetails(instanceRepository).getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocation -> {
            EciInstanceEntity entity = invocation.getArgument(0);
            InstanceSave saved = new InstanceSave(entity.getId(), entity.getStatus(), entity.getFailureReason());
            Object result = repositoryAnswer.answer(invocation);
            instanceSaves.add(saved);
            return result;
        }).when(instanceRepository).save(any(EciInstanceEntity.class));
        originalCreateTimeoutSec = properties.getCreateTimeoutSec();
        originalFailureLogTailLines = properties.getFailureLogTailLines();
        originalFailureLogGraceSec = properties.getFailureLogGraceSec();
    }

    @AfterEach
    void restoreRuntimeSettings() {
        configService.saveAdvanced(Map.of(
                "ECI_CREATE_TIMEOUT_SEC", Integer.toString(originalCreateTimeoutSec),
                "ECI_FAILURE_LOG_TAIL_LINES", Integer.toString(originalFailureLogTailLines),
                "ECI_FAILURE_LOG_GRACE_SEC", Integer.toString(originalFailureLogGraceSec)));
    }

    @Test
    void createTimeoutUsesSubmissionSnapshotAfterCurrentTimeoutChanges() {
        configService.saveAdvanced(Map.of("ECI_CREATE_TIMEOUT_SEC", "12"));
        UUID shortTimeoutInstanceId = createTask().instanceId();
        assertThat(instance(shortTimeoutInstanceId).getCreateTimeoutSec()).isEqualTo(12);

        String shortTimeoutCloudId = "eci-timeout-short-" + shortTimeoutInstanceId;
        UUID shortTimeoutActionId = prepareWaitingAction(
                shortTimeoutInstanceId, shortTimeoutCloudId, NOW.minusSeconds(13));
        configService.saveAdvanced(Map.of("ECI_CREATE_TIMEOUT_SEC", "120"));
        when(provider.describe(shortTimeoutCloudId, REGION_ID))
                .thenReturn(new CloudInstanceView(shortTimeoutCloudId, "Pending", null));

        processAt(shortTimeoutActionId);

        assertThat(failedSave(shortTimeoutInstanceId).status()).isEqualTo(InstanceStatus.FAILED);
        assertThat(failedSave(shortTimeoutInstanceId).failureReason()).contains("等待 Running 超时（12s）");
        assertThat(instance(shortTimeoutInstanceId).getFailureReason())
                .contains("等待 Running 超时（12s）");
        assertDeleteScheduledAt(shortTimeoutInstanceId, NOW);

        configService.saveAdvanced(Map.of("ECI_CREATE_TIMEOUT_SEC", "120"));
        UUID longTimeoutInstanceId = createTask().instanceId();
        assertThat(instance(longTimeoutInstanceId).getCreateTimeoutSec()).isEqualTo(120);

        String longTimeoutCloudId = "eci-timeout-long-" + longTimeoutInstanceId;
        UUID longTimeoutActionId = prepareWaitingAction(
                longTimeoutInstanceId, longTimeoutCloudId, NOW.minusSeconds(13));
        configService.saveAdvanced(Map.of("ECI_CREATE_TIMEOUT_SEC", "12"));
        when(provider.describe(longTimeoutCloudId, REGION_ID))
                .thenReturn(new CloudInstanceView(longTimeoutCloudId, "Pending", null));

        processAt(longTimeoutActionId);

        assertThat(instance(longTimeoutInstanceId).getStatus()).isEqualTo(InstanceStatus.CREATING);
        assertThat(actionRepository.findById(longTimeoutActionId).orElseThrow().getStatus())
                .isEqualTo(LifecycleActionStatus.WAITING);
    }

    @Test
    void createFailureUsesAssociatedTaskLogAndGraceSnapshots() {
        configService.saveAdvanced(Map.of(
                "ECI_FAILURE_LOG_TAIL_LINES", "37",
                "ECI_FAILURE_LOG_GRACE_SEC", "41"));
        UUID instanceId = createTask().instanceId();
        EciTaskEntity task = taskFor(instanceId);
        assertThat(task.getFailureLogTailLines()).isEqualTo(37);
        assertThat(task.getFailureLogGraceSec()).isEqualTo(41);

        configService.saveAdvanced(Map.of(
                "ECI_FAILURE_LOG_TAIL_LINES", "5",
                "ECI_FAILURE_LOG_GRACE_SEC", "7"));
        String cloudId = "eci-failed-snapshot-" + instanceId;
        UUID actionId = prepareWaitingAction(instanceId, cloudId, NOW);
        when(provider.describe(cloudId, REGION_ID))
                .thenReturn(new CloudInstanceView(cloudId, "Failed", "exit 7"));
        when(provider.fetchContainerLog(cloudId, 37, REGION_ID)).thenReturn("container output");

        processAt(actionId);

        assertThat(failedSave(instanceId).status()).isEqualTo(InstanceStatus.FAILED);
        assertThat(failedSave(instanceId).failureReason()).contains("exit 7");
        assertThat(taskFor(instanceId).getContainerLogTail()).isEqualTo("container output");
        LifecycleActionEntity delete = actionRepository
                .findByInstanceIdAndActionType(instanceId, LifecycleActionType.DELETE).orElseThrow();
        assertThat(delete.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(41));
        verify(provider).describe(cloudId, REGION_ID);
        verify(provider).fetchContainerLog(cloudId, 37, REGION_ID);
    }

    @Test
    void createFailureFallsBackForLegacyAndManualInstancesAndSurvivesLogFailure() {
        configService.saveAdvanced(Map.of(
                "ECI_FAILURE_LOG_TAIL_LINES", "29",
                "ECI_FAILURE_LOG_GRACE_SEC", "31"));
        UUID legacyInstanceId = createTask().instanceId();
        EciTaskEntity legacyTask = taskFor(legacyInstanceId);
        legacyTask.setFailureLogTailLines(null);
        legacyTask.setFailureLogGraceSec(null);
        taskRepository.saveAndFlush(legacyTask);

        String legacyCloudId = "eci-failed-legacy-" + legacyInstanceId;
        UUID legacyActionId = prepareWaitingAction(legacyInstanceId, legacyCloudId, NOW);
        when(provider.describe(legacyCloudId, REGION_ID))
                .thenReturn(new CloudInstanceView(legacyCloudId, "Failed", "legacy failure"));
        doThrow(new IllegalStateException("logs unavailable"))
                .when(provider).fetchContainerLog(legacyCloudId, 29, REGION_ID);

        processAt(legacyActionId);

        assertThat(failedSave(legacyInstanceId).status()).isEqualTo(InstanceStatus.FAILED);
        assertThat(failedSave(legacyInstanceId).failureReason()).contains("legacy failure");
        assertThat(taskFor(legacyInstanceId).getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(taskFor(legacyInstanceId).getContainerLogTail()).isNull();
        assertDeleteScheduledAt(legacyInstanceId, NOW.plusSeconds(31));
        verify(provider).fetchContainerLog(legacyCloudId, 29, REGION_ID);

        UUID manualInstanceId = saveManualInstance("eci-failed-manual");
        UUID manualActionId = prepareWaitingAction(manualInstanceId, "eci-failed-manual", NOW);
        when(provider.describe("eci-failed-manual", REGION_ID))
                .thenReturn(new CloudInstanceView("eci-failed-manual", "Failed", "manual failure"));
        when(provider.fetchContainerLog("eci-failed-manual", 29, REGION_ID)).thenReturn("manual output");

        processAt(manualActionId);

        assertThat(failedSave(manualInstanceId).status()).isEqualTo(InstanceStatus.FAILED);
        assertThat(failedSave(manualInstanceId).failureReason()).contains("manual failure");
        assertDeleteScheduledAt(manualInstanceId, NOW.plusSeconds(31));
        verify(provider).fetchContainerLog("eci-failed-manual", 29, REGION_ID);
    }

    private InstanceSave failedSave(UUID instanceId) {
        return instanceSaves.stream()
                .filter(saved -> instanceId.equals(saved.instanceId()) && saved.status() == InstanceStatus.FAILED)
                .findFirst()
                .orElseThrow(() -> new AssertionError("FAILED instance was not saved: " + instanceId));
    }

    private record InstanceSave(UUID instanceId, InstanceStatus status, String failureReason) {
    }

    private TaskResponse createTask() {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setImage("nginx:alpine");
        return taskService.create(request);
    }

    private EciTaskEntity taskFor(UUID instanceId) {
        return taskRepository.findById(instance(instanceId).getTaskId()).orElseThrow();
    }

    private EciInstanceEntity instance(UUID instanceId) {
        return instanceRepository.findById(instanceId).orElseThrow();
    }

    private UUID prepareWaitingAction(UUID instanceId, String cloudId, Instant createdAt) {
        EciInstanceEntity instance = instance(instanceId);
        instance.setStatus(InstanceStatus.CREATING);
        instance.setCloudInstanceId(cloudId);
        instance.setRegionId(REGION_ID);
        instance.setCreatedAt(createdAt);
        instance.setUpdatedAt(NOW);
        instanceRepository.saveAndFlush(instance);

        LifecycleActionEntity action = actionRepository
                .findByInstanceIdAndActionType(instanceId, LifecycleActionType.CREATE)
                .orElseGet(() -> newCreateAction(instanceId));
        action.setPhase(LifecycleActionPhase.WAIT_RUNNING);
        action.setStatus(LifecycleActionStatus.READY);
        action.setAttemptCount(1);
        action.setCreatedAt(createdAt);
        action.setUpdatedAt(NOW);
        action.setNextAttemptAt(NOW);
        actionRepository.saveAndFlush(action);
        return action.getId();
    }

    private UUID saveManualInstance(String cloudId) {
        EciInstanceEntity instance = new EciInstanceEntity();
        instance.setId(UUID.randomUUID());
        instance.setName("manual-instance");
        instance.setImage("nginx:alpine");
        instance.setCpu(1.0);
        instance.setMemory(1.0);
        instance.setStatus(InstanceStatus.CREATING);
        instance.setCloudInstanceId(cloudId);
        instance.setRegionId(REGION_ID);
        instance.setCreatedAt(NOW);
        instance.setUpdatedAt(NOW);
        instanceRepository.saveAndFlush(instance);
        return instance.getId();
    }

    private LifecycleActionEntity newCreateAction(UUID instanceId) {
        LifecycleActionEntity action = new LifecycleActionEntity();
        action.setId(UUID.randomUUID());
        action.setInstanceId(instanceId);
        action.setActionType(LifecycleActionType.CREATE);
        action.setClientToken(instanceId.toString());
        return action;
    }

    private void assertDeleteScheduledAt(UUID instanceId, Instant notBefore) {
        LifecycleActionEntity delete = actionRepository
                .findByInstanceIdAndActionType(instanceId, LifecycleActionType.DELETE).orElseThrow();
        assertThat(delete.getNextAttemptAt()).isEqualTo(notBefore);
    }

    private void processAt(UUID actionId) {
        UUID leaseToken = actionService.claim(actionId, NOW).orElseThrow().leaseToken();
        processor.process(actionId, leaseToken);
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
