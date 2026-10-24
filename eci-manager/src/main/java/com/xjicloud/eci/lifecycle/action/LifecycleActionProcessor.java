package com.xjicloud.eci.lifecycle.action;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.instance.EciInstanceEntity;
import com.xjicloud.eci.instance.EciInstanceRepository;
import com.xjicloud.eci.provider.CloudInstanceView;
import com.xjicloud.eci.provider.CloudOperationException;
import com.xjicloud.eci.provider.CloudOperationKind;
import com.xjicloud.eci.provider.CreateSpec;
import com.xjicloud.eci.provider.EciProvider;
import com.xjicloud.eci.task.EciTaskEntity;
import com.xjicloud.eci.task.EciTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class LifecycleActionProcessor {

    private static final Logger log = LoggerFactory.getLogger(LifecycleActionProcessor.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DELETE_TIMEOUT_MARKER = "DELETE_TIMEOUT";
    private static final int LAST_ERROR_MAX_LENGTH = 1000;

    private final LifecycleActionRepository actionRepository;
    private final EciInstanceRepository instanceRepository;
    private final LifecycleActionService actionService;
    private final EciProvider provider;
    private final EciProperties properties;
    private final Clock clock;
    private final EciTaskRepository taskRepository;

    public LifecycleActionProcessor(
            LifecycleActionRepository actionRepository,
            EciInstanceRepository instanceRepository,
            LifecycleActionService actionService,
            EciProvider provider,
            EciProperties properties,
            Clock clock,
            EciTaskRepository taskRepository) {
        this.actionRepository = actionRepository;
        this.instanceRepository = instanceRepository;
        this.actionService = actionService;
        this.provider = provider;
        this.properties = properties;
        this.clock = clock;
        this.taskRepository = taskRepository;
    }

    public void process(UUID actionId, UUID leaseToken) {
        LifecycleActionEntity action = actionRepository.findById(actionId).orElse(null);
        if (!hasLease(action, leaseToken)) {
            return;
        }
        if (action.getActionType() == LifecycleActionType.CREATE) {
            processCreate(action, leaseToken);
            return;
        }
        if (action.getActionType() != LifecycleActionType.DELETE) {
            log.error("unsupported lifecycle action type actionId={} actionType={}", actionId, action.getActionType());
            return;
        }

        EciInstanceEntity instance = instanceRepository.findById(action.getInstanceId()).orElse(null);
        if (instance == null) {
            recordFailure(action, leaseToken, new IllegalStateException("instance not found"));
            return;
        }

        String cloudId = instance.getCloudInstanceId();
        if (cloudId == null || cloudId.isBlank()) {
            actionService.resolveDeleteWithoutCloud(
                    instance.getId(), actionId, leaseToken, action.getUpdatedAt(),
                    clock.instant().plusMillis(properties.getPollIntervalMs()));
            return;
        }

        if (action.getPhase() == LifecycleActionPhase.DELETE_REQUEST) {
            requestCloudDelete(action, leaseToken, cloudId, instance.getRegionId());
        } else if (action.getPhase() == LifecycleActionPhase.WAIT_DELETED) {
            pollCloudDeletion(action, leaseToken, cloudId, instance.getRegionId());
        } else {
            recordFailure(action, leaseToken,
                    new IllegalStateException("unsupported DELETE phase: " + action.getPhase()));
        }
    }

    private void processCreate(LifecycleActionEntity action, UUID leaseToken) {
        EciInstanceEntity instance = instanceRepository.findById(action.getInstanceId()).orElse(null);
        if (instance == null) {
            actionService.failCreate(action.getId(), leaseToken, "instance not found");
            return;
        }
        if (isLegacyUnknownMarker(action)) {
            if (LifecycleActionService.LEGACY_CREATE_OUTCOME_UNKNOWN.equals(action.getLastError())) {
                log.error("legacy CREATE quarantined; manual reconciliation required actionId={} instanceId={}",
                        action.getId(), action.getInstanceId());
            }
            actionService.quarantineLegacyCreate(action.getId(), leaseToken);
            return;
        }
        if (!action.getInstanceId().toString().equals(action.getClientToken())) {
            actionService.failCreate(action.getId(), leaseToken, "CREATE client token does not match local instance");
            return;
        }
        int createTimeoutSec = instance.getCreateTimeoutSec() != null
                ? instance.getCreateTimeoutSec()
                : properties.getCreateTimeoutSec();
        if (isCreateTimedOut(action, createTimeoutSec)) {
            actionService.timeoutCreateBusiness(action.getId(), leaseToken, createTimeoutSec);
            if (hasText(instance.getCloudInstanceId())) {
                actionService.finishCreateAndRequestDelete(action.getId(), leaseToken);
                return;
            }
        }
        if (action.getPhase() == LifecycleActionPhase.CREATE_REQUEST) {
            requestCloudCreate(action, leaseToken, instance);
        } else if (action.getPhase() == LifecycleActionPhase.WAIT_RUNNING) {
            pollCloudCreate(action, leaseToken, instance);
        } else {
            actionService.failCreate(action.getId(), leaseToken,
                    "unsupported CREATE phase: " + action.getPhase());
        }
    }

    private void requestCloudCreate(
            LifecycleActionEntity action, UUID leaseToken, EciInstanceEntity instance) {
        if (instance.getStatus() == com.xjicloud.eci.instance.InstanceStatus.STOPPING
                || instance.getStatus() == com.xjicloud.eci.instance.InstanceStatus.TERMINATED) {
            if (action.getCreateCallStartedAt() == null) {
                actionService.cancelCreateBeforeProviderCall(action.getId(), leaseToken);
                return;
            }
        }
        CreateSpec spec;
        try {
            spec = createSpec(instance);
        } catch (Exception exception) {
            actionService.failCreate(action.getId(), leaseToken, messageOf(exception));
            return;
        }
        try {
            if (!actionService.markCreateCallStarted(action.getId(), leaseToken)) {
                return;
            }
            String cloudId = provider.create(spec, action.getClientToken());
            actionService.acceptCreate(action.getId(), leaseToken, cloudId);
        } catch (CloudOperationException exception) {
            if (exception.kind() == CloudOperationKind.REJECTED) {
                actionService.failCreate(action.getId(), leaseToken, messageOf(exception));
            } else {
                actionService.retryCreate(action.getId(), leaseToken, messageOf(exception));
            }
        } catch (Exception exception) {
            actionService.retryCreate(action.getId(), leaseToken, messageOf(exception));
        }
    }

    private void pollCloudCreate(
            LifecycleActionEntity action, UUID leaseToken, EciInstanceEntity instance) {
        if (instance.getCloudInstanceId() == null || instance.getCloudInstanceId().isBlank()) {
            actionService.retryCreate(action.getId(), leaseToken, "CREATE accepted without cloudInstanceId");
            return;
        }
        CloudInstanceView view;
        try {
            view = provider.describe(instance.getCloudInstanceId(), instance.getRegionId());
        } catch (Exception exception) {
            actionService.retryCreate(action.getId(), leaseToken, messageOf(exception));
            return;
        }
        if (view.isRunning() || view.isSucceeded()) {
            actionService.completeCreateRunning(
                    action.getId(), leaseToken, view.status(), view.statusDetail());
        } else if (view.isFailed()) {
            String reason = view.statusDetail() == null ? view.status() : view.statusDetail();
            FailureCaptureSettings settings = failureCaptureSettings(instance);
            actionService.completeCreateFailedAndRequestDelete(action.getId(), leaseToken, reason,
                    fetchLogSafe(instance.getCloudInstanceId(), settings.tailLines(), instance.getRegionId()),
                    view.status(), view.statusDetail(), settings.graceSec());
        } else {
            actionService.waitForRunning(action.getId(), leaseToken, view.status(), view.statusDetail());
        }
    }

    private FailureCaptureSettings failureCaptureSettings(EciInstanceEntity instance) {
        EciTaskEntity task = instance.getTaskId() == null
                ? null
                : taskRepository.findById(instance.getTaskId()).orElse(null);
        int tailLines = task != null && task.getFailureLogTailLines() != null
                ? task.getFailureLogTailLines()
                : properties.getFailureLogTailLines();
        int graceSec = task != null && task.getFailureLogGraceSec() != null
                ? task.getFailureLogGraceSec()
                : properties.getFailureLogGraceSec();
        return new FailureCaptureSettings(tailLines, Math.max(0, graceSec));
    }

    private String fetchLogSafe(String cloudInstanceId, int tailLines, String regionId) {
        try {
            String logTail = provider.fetchContainerLog(cloudInstanceId, tailLines, regionId);
            return logTail == null || logTail.isBlank() ? null : logTail;
        } catch (Exception exception) {
            log.warn("fetchContainerLog failed cloudInstanceId={}: {}", cloudInstanceId, exception.getMessage());
            return null;
        }
    }

    private CreateSpec createSpec(EciInstanceEntity instance) {
        String regionId = instance.getRegionId();
        String vswitchId = instance.getVswitchId();
        String securityGroupId = instance.getSecurityGroupId();
        if (!properties.isMock()) {
            // 在写入未知调用标记前校验，并固定旧行的回退值，避免 Provider 再读取变化后的配置。
            regionId = requireCreateNetworkValue("regionId",
                    regionId != null ? regionId : properties.getRegionId());
            vswitchId = requireCreateNetworkValue("vswitchId",
                    vswitchId != null ? vswitchId : properties.getVswitchId());
            securityGroupId = requireCreateNetworkValue("securityGroupId",
                    securityGroupId != null ? securityGroupId : properties.getSecurityGroupId());
        }
        return new CreateSpec(
                instance.getName(),
                instance.getImage(),
                instance.getCpu(),
                instance.getMemory(),
                readMap(instance.getEnvJson()),
                instance.getInstanceType(),
                instance.getGpu(),
                instance.getRestartPolicy(),
                readList(instance.getCommandJson()),
                readList(instance.getArgsJson()),
                instance.getImageSnapshotId(),
                instance.getShmSize(),
                regionId,
                instance.getVpcId(),
                vswitchId,
                securityGroupId);
    }

    private static String requireCreateNetworkValue(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private Map<String, String> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid persisted env JSON", exception);
        }
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid persisted command JSON", exception);
        }
    }

    private boolean isCreateTimedOut(LifecycleActionEntity action, int createTimeoutSec) {
        return action.getCreatedAt() != null
                && clock.instant().isAfter(action.getCreatedAt().plusSeconds(createTimeoutSec));
    }

    private static boolean isLegacyUnknownMarker(LifecycleActionEntity action) {
        return action.getPhase() == LifecycleActionPhase.CREATE_REQUEST
                && (LifecycleActionService.LEGACY_CREATE_OUTCOME_UNKNOWN.equals(action.getLastError())
                || LifecycleActionService.LEGACY_CREATE_QUARANTINED.equals(action.getLastError()));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String messageOf(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private void requestCloudDelete(
            LifecycleActionEntity action, UUID leaseToken, String cloudId, String regionId) {
        try {
            provider.delete(cloudId, regionId);
        } catch (Exception exception) {
            recordFailure(action, leaseToken, exception);
            return;
        }

        Instant now = clock.instant();
        actionService.updateDeleteStep(action.getId(), leaseToken,
                action.getUpdatedAt(),
                LifecycleActionPhase.WAIT_DELETED,
                now.plusMillis(properties.getPollIntervalMs()),
                action.getAttemptCount(), null);
    }

    private void pollCloudDeletion(
            LifecycleActionEntity action, UUID leaseToken, String cloudId, String regionId) {
        CloudInstanceView view;
        try {
            view = provider.describe(cloudId, regionId);
        } catch (Exception exception) {
            recordFailure(action, leaseToken, exception);
            return;
        }

        if (view.isTerminatedOrMissing()) {
            actionService.completeInstanceTermination(action.getInstanceId(), action.getId(), leaseToken);
            return;
        }

        actionService.recordProviderStatus(action.getInstanceId(), view.status(), view.statusDetail());

        Instant now = clock.instant();
        boolean timedOut = action.getCreatedAt() != null
                && now.isAfter(action.getCreatedAt().plusSeconds(properties.getDeleteTimeoutSec()));
        String lastError = timedOut ? DELETE_TIMEOUT_MARKER : null;
        if (timedOut && !DELETE_TIMEOUT_MARKER.equals(action.getLastError())) {
            log.error("DELETE_TIMEOUT actionId={} instanceId={} cloudInstanceId={} timeoutSec={}",
                    action.getId(), action.getInstanceId(), cloudId, properties.getDeleteTimeoutSec());
        }

        actionService.updateDeleteStep(action.getId(), leaseToken,
                action.getUpdatedAt(),
                LifecycleActionPhase.WAIT_DELETED,
                now.plusMillis(properties.getPollIntervalMs()),
                action.getAttemptCount(), lastError);
    }

    private void recordFailure(
            LifecycleActionEntity action,
            UUID leaseToken,
            Exception exception) {
        int attemptCount = action.getAttemptCount() + 1;
        String error = exception.getMessage();
        if (error == null || error.isBlank()) {
            error = exception.getClass().getSimpleName();
        }
        if (error.length() > LAST_ERROR_MAX_LENGTH) {
            error = error.substring(0, LAST_ERROR_MAX_LENGTH);
        }

        int alertAttempt = properties.getLifecycleAction().getCleanupAlertAttempts();
        if (attemptCount >= alertAttempt && (attemptCount - alertAttempt) % 10 == 0) {
            log.error("DELETE cleanup retry actionId={} instanceId={} attempt={} error={}",
                    action.getId(), action.getInstanceId(), attemptCount, error);
        } else {
            log.warn("DELETE cleanup retry actionId={} instanceId={} attempt={} error={}",
                    action.getId(), action.getInstanceId(), attemptCount, error);
        }

        Instant now = clock.instant();
        EciProperties.LifecycleAction retry = properties.getLifecycleAction();
        Instant nextAttemptAt = LifecycleActionBackoff.nextAttemptAt(now, attemptCount,
                retry.getRetryInitialSec(), retry.getRetryMaxSec());
        actionService.updateDeleteStep(action.getId(), leaseToken, action.getUpdatedAt(), action.getPhase(),
                nextAttemptAt, attemptCount, error);
    }

    private record FailureCaptureSettings(int tailLines, int graceSec) {
    }

    private static boolean hasLease(LifecycleActionEntity action, UUID leaseToken) {
        return action != null
                && action.getStatus() == LifecycleActionStatus.IN_PROGRESS
                && leaseToken != null
                && leaseToken.equals(action.getLeaseToken());
    }
}
