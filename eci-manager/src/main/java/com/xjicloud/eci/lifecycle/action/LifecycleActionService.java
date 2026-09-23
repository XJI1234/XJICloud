package com.xjicloud.eci.lifecycle.action;

import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.instance.EciInstanceEntity;
import com.xjicloud.eci.instance.EciInstanceRepository;
import com.xjicloud.eci.instance.InstanceStatus;
import com.xjicloud.eci.task.EciTaskService;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class LifecycleActionService {

    static final String LEGACY_CREATE_OUTCOME_UNKNOWN = "Legacy CREATE outcome unknown";
    static final String LEGACY_CREATE_QUARANTINED =
            "Legacy CREATE outcome unknown; manual reconciliation required";

    private static final Set<LifecycleActionStatus> CLAIMABLE_STATUSES =
            Set.of(LifecycleActionStatus.READY, LifecycleActionStatus.WAITING);
    private static final Set<LifecycleActionType> ACTION_TYPES =
            Set.of(LifecycleActionType.CREATE, LifecycleActionType.DELETE);
    private static final int LAST_ERROR_MAX_LENGTH = 1000;

    public enum NoCloudDeleteResult {
        TERMINATED,
        WAITING,
        LOST_LEASE
    }

    private final LifecycleActionRepository repository;
    private final EciInstanceRepository instanceRepository;
    private final EciProperties properties;
    private final Clock clock;
    private final EciTaskService taskService;

    public LifecycleActionService(
            LifecycleActionRepository repository,
            EciInstanceRepository instanceRepository,
            EciProperties properties,
            Clock clock,
            @Lazy EciTaskService taskService) {
        this.repository = repository;
        this.instanceRepository = instanceRepository;
        this.properties = properties;
        this.clock = clock;
        this.taskService = taskService;
    }

    @Transactional
    public void requestDelete(UUID instanceId, Instant notBefore) {
        Instant now = clock.instant();
        EciInstanceEntity instance = instanceRepository.findForUpdateById(instanceId)
                .orElseThrow(() -> new IllegalStateException("instance not found: " + instanceId));
        ensureLegacyCreateOutcomeMarker(instance, now);
        LifecycleActionEntity action = repository.findForUpdateByInstanceIdAndActionType(
                        instanceId, LifecycleActionType.DELETE)
                .orElseGet(() -> newDeleteAction(instanceId, notBefore));

        if (action.getStatus() != LifecycleActionStatus.SUCCEEDED
                && action.getNextAttemptAt().isAfter(notBefore)) {
            action.setNextAttemptAt(notBefore);
            action.setUpdatedAt(now);
        }
        if (action.getStatus() == LifecycleActionStatus.IN_PROGRESS) {
            Instant requestedAt = now.isAfter(action.getUpdatedAt())
                    ? now
                    : action.getUpdatedAt().plusMillis(1);
            action.setUpdatedAt(requestedAt);
        }
        if (instance.getStatus() != InstanceStatus.TERMINATED) {
            boolean preserveFailureReason = instance.getStatus() == InstanceStatus.FAILED;
            instance.setStatus(InstanceStatus.STOPPING);
            if (!preserveFailureReason) {
                instance.setFailureReason(null);
            }
            instance.setUpdatedAt(now);
            instanceRepository.save(instance);
        }
        repository.save(action);
    }

    private void ensureLegacyCreateOutcomeMarker(EciInstanceEntity instance, Instant now) {
        if (instance.getStatus() == InstanceStatus.TERMINATED
                || (instance.getCloudInstanceId() != null && !instance.getCloudInstanceId().isBlank())
                || repository.findForUpdateByInstanceIdAndActionType(
                        instance.getId(), LifecycleActionType.CREATE).isPresent()) {
            return;
        }

        LifecycleActionEntity action = new LifecycleActionEntity();
        action.setId(UUID.randomUUID());
        action.setInstanceId(instance.getId());
        action.setActionType(LifecycleActionType.CREATE);
        action.setPhase(LifecycleActionPhase.CREATE_REQUEST);
        action.setStatus(LifecycleActionStatus.WAITING);
        action.setClientToken(instance.getId().toString());
        action.setAttemptCount(1);
        action.setNextAttemptAt(now);
        action.setLastError(LEGACY_CREATE_OUTCOME_UNKNOWN);
        action.setCreatedAt(now);
        action.setUpdatedAt(now);
        repository.save(action);
    }

    @Transactional
    public void createCreateAction(UUID instanceId) {
        Instant now = clock.instant();
        EciInstanceEntity instance = instanceRepository.findForUpdateById(instanceId)
                .orElseThrow(() -> new IllegalStateException("instance not found: " + instanceId));
        if (instance.getStatus() != InstanceStatus.CREATING) {
            throw new IllegalStateException("instance is not CREATING: " + instanceId);
        }
        if (repository.findForUpdateByInstanceIdAndActionType(instanceId, LifecycleActionType.CREATE)
                .isPresent()) {
            return;
        }

        LifecycleActionEntity action = new LifecycleActionEntity();
        action.setId(UUID.randomUUID());
        action.setInstanceId(instanceId);
        action.setActionType(LifecycleActionType.CREATE);
        action.setPhase(LifecycleActionPhase.CREATE_REQUEST);
        action.setStatus(LifecycleActionStatus.READY);
        action.setClientToken(instanceId.toString());
        action.setAttemptCount(0);
        action.setNextAttemptAt(now);
        action.setCreatedAt(now);
        action.setUpdatedAt(now);
        repository.save(action);
    }

    /**
     * 启动补偿只补齐已有新流程的 CREATE；缺失动作的旧记录保守隔离，避免用伪造 token 重放旧请求。
     */
    @Transactional
    public void ensureCreateAction(UUID instanceId) {
        Instant now = clock.instant();
        EciInstanceEntity instance = instanceRepository.findForUpdateById(instanceId)
                .orElseThrow(() -> new IllegalStateException("instance not found: " + instanceId));
        if (repository.findForUpdateByInstanceIdAndActionType(instanceId, LifecycleActionType.CREATE).isPresent()) {
            return;
        }
        if (instance.getStatus() != InstanceStatus.CREATING) {
            return;
        }
        if (!hasText(instance.getCloudInstanceId())) {
            ensureLegacyCreateOutcomeMarker(instance, now);
            return;
        }

        LifecycleActionEntity action = new LifecycleActionEntity();
        action.setId(UUID.randomUUID());
        action.setInstanceId(instanceId);
        action.setActionType(LifecycleActionType.CREATE);
        action.setPhase(LifecycleActionPhase.WAIT_RUNNING);
        action.setStatus(LifecycleActionStatus.READY);
        action.setClientToken(instanceId.toString());
        action.setAttemptCount(0);
        action.setNextAttemptAt(now);
        action.setCreatedAt(now);
        action.setUpdatedAt(now);
        repository.save(action);
    }

    @Transactional
    public void cancelCreateBeforeProviderCall(UUID actionId, UUID leaseToken) {
        LockedCreate locked = lockCreateInstanceAndAction(actionId);
        LifecycleActionEntity action = locked == null ? null : locked.action();
        EciInstanceEntity instance = locked == null ? null : locked.instance();
        if (instance == null || !hasLease(action, leaseToken)) {
            return;
        }
        cancelCreateAction(action, clock.instant());
        repository.save(action);
    }

    /** Persist the ambiguity marker before invoking the external Create API when the lease is valid. */
    @Transactional
    public boolean markCreateCallStarted(UUID actionId, UUID leaseToken) {
        LifecycleActionEntity action = repository.findForUpdateById(actionId).orElse(null);
        if (!hasLease(action, leaseToken)) {
            return false;
        }
        if (action.getCreateCallStartedAt() != null) {
            return true;
        }
        Instant now = clock.instant();
        action.setCreateCallStartedAt(now);
        action.setUpdatedAt(now);
        repository.save(action);
        return true;
    }

    @Transactional
    public void quarantineLegacyCreate(UUID actionId, UUID leaseToken) {
        LifecycleActionEntity action = repository.findForUpdateById(actionId).orElse(null);
        if (!hasLease(action, leaseToken)) {
            return;
        }
        Instant now = clock.instant();
        if (LEGACY_CREATE_OUTCOME_UNKNOWN.equals(action.getLastError())) {
            action.setLastError(LEGACY_CREATE_QUARANTINED);
        }
        action.setStatus(LifecycleActionStatus.WAITING);
        action.setNextAttemptAt(LifecycleActionBackoff.nextAttemptAt(now,
                Math.max(1, action.getAttemptCount()),
                properties.getLifecycleAction().getRetryInitialSec(),
                properties.getLifecycleAction().getRetryMaxSec()));
        action.setLeaseToken(null);
        action.setLeaseUntil(null);
        action.setUpdatedAt(now);
        repository.save(action);
    }

    @Transactional
    public void acceptCreate(UUID actionId, UUID leaseToken, String cloudInstanceId) {
        if (!hasText(cloudInstanceId)) {
            throw new IllegalArgumentException("cloudInstanceId is required");
        }
        LockedCreate locked = lockCreateInstanceAndAction(actionId);
        LifecycleActionEntity action = locked == null ? null : locked.action();
        EciInstanceEntity instance = locked == null ? null : locked.instance();
        if (instance == null || !hasLease(action, leaseToken)) {
            return;
        }
        Instant now = clock.instant();
        instance.setCloudInstanceId(cloudInstanceId);
        instance.setCloudStatus("Pending");
        instance.setUpdatedAt(now);
        action.setPhase(LifecycleActionPhase.WAIT_RUNNING);
        action.setStatus(LifecycleActionStatus.WAITING);
        action.setNextAttemptAt(now.plusMillis(properties.getPollIntervalMs()));
        action.setLeaseToken(null);
        action.setLeaseUntil(null);
        action.setLastError(null);
        action.setUpdatedAt(now);
        instanceRepository.save(instance);
        repository.save(action);
        if (instance.getStatus() != InstanceStatus.CREATING) {
            requestDelete(instance.getId(), now);
        }
    }

    @Transactional
    public void retryCreate(UUID actionId, UUID leaseToken, String error) {
        LifecycleActionEntity action = repository.findForUpdateById(actionId).orElse(null);
        if (!hasLease(action, leaseToken)) {
            return;
        }
        Instant now = clock.instant();
        int attempts = action.getAttemptCount() + 1;
        action.setStatus(LifecycleActionStatus.WAITING);
        action.setAttemptCount(attempts);
        action.setNextAttemptAt(LifecycleActionBackoff.nextAttemptAt(now, attempts,
                properties.getLifecycleAction().getRetryInitialSec(),
                properties.getLifecycleAction().getRetryMaxSec()));
        action.setLastError(truncate(error));
        action.setLeaseToken(null);
        action.setLeaseUntil(null);
        action.setUpdatedAt(now);
        repository.save(action);
    }

    @Transactional
    public void waitForRunning(UUID actionId, UUID leaseToken, String cloudStatus) {
        LockedCreate locked = lockCreateInstanceAndAction(actionId);
        LifecycleActionEntity action = locked == null ? null : locked.action();
        EciInstanceEntity instance = locked == null ? null : locked.instance();
        if (instance == null || !hasLease(action, leaseToken)) {
            return;
        }
        Instant now = clock.instant();
        instance.setCloudStatus(cloudStatus);
        instance.setUpdatedAt(now);
        action.setStatus(LifecycleActionStatus.WAITING);
        action.setNextAttemptAt(now.plusMillis(properties.getPollIntervalMs()));
        action.setLeaseToken(null);
        action.setLeaseUntil(null);
        action.setUpdatedAt(now);
        instanceRepository.save(instance);
        repository.save(action);
    }

    @Transactional
    public void completeCreateRunning(UUID actionId, UUID leaseToken, String cloudStatus) {
        LockedCreate locked = lockCreateInstanceAndAction(actionId);
        LifecycleActionEntity action = locked == null ? null : locked.action();
        EciInstanceEntity instance = locked == null ? null : locked.instance();
        if (instance == null || !hasLease(action, leaseToken)) {
            return;
        }
        Instant now = clock.instant();
        if (instance.getStatus() == InstanceStatus.CREATING) {
            instance.setStatus(InstanceStatus.RUNNING);
            instance.setCloudStatus(cloudStatus);
            instance.setFailureReason(null);
            instance.setUpdatedAt(now);
            instanceRepository.save(instance);
            if (instance.getTaskId() != null) {
                taskService.onInstanceRunning(instance.getTaskId());
            }
        } else {
            requestDelete(instance.getId(), now);
        }
        completeAction(action, now);
        repository.save(action);
    }

    @Transactional
    public void failCreate(UUID actionId, UUID leaseToken, String reason) {
        LockedCreate locked = lockCreateInstanceAndAction(actionId);
        LifecycleActionEntity action = locked == null ? null : locked.action();
        EciInstanceEntity instance = locked == null ? null : locked.instance();
        if (instance == null || !hasLease(action, leaseToken)) {
            return;
        }
        Instant now = clock.instant();
        failBusinessInstance(instance, reason, now);
        action.setStatus(LifecycleActionStatus.FAILED);
        action.setLeaseToken(null);
        action.setLeaseUntil(null);
        action.setLastError(truncate(reason));
        action.setCompletedAt(now);
        action.setUpdatedAt(now);
        instanceRepository.save(instance);
        repository.save(action);
    }

    @Transactional
    public void completeCreateFailedAndRequestDelete(UUID actionId, UUID leaseToken, String reason, String logTail) {
        LockedCreate locked = lockCreateInstanceAndAction(actionId);
        LifecycleActionEntity action = locked == null ? null : locked.action();
        EciInstanceEntity instance = locked == null ? null : locked.instance();
        if (instance == null || !hasLease(action, leaseToken)) {
            return;
        }
        Instant now = clock.instant();
        failBusinessInstance(instance, reason, logTail, now);
        instanceRepository.save(instance);
        if (hasText(instance.getCloudInstanceId())) {
            requestDelete(instance.getId(), now.plusSeconds(Math.max(0, properties.getFailureLogGraceSec())));
        }
        completeAction(action, now);
        repository.save(action);
    }

    @Transactional
    public void timeoutCreateBusiness(UUID actionId, UUID leaseToken) {
        LockedCreate locked = lockCreateInstanceAndAction(actionId);
        LifecycleActionEntity action = locked == null ? null : locked.action();
        EciInstanceEntity instance = locked == null ? null : locked.instance();
        if (instance == null || !hasLease(action, leaseToken)) {
            return;
        }
        Instant now = clock.instant();
        if (instance.getStatus() == InstanceStatus.CREATING || instance.getStatus() == InstanceStatus.RUNNING) {
            failBusinessInstance(instance, "等待 Running 超时（" + properties.getCreateTimeoutSec() + "s）", now);
            instanceRepository.save(instance);
        }
    }

    @Transactional
    public void finishCreateAndRequestDelete(UUID actionId, UUID leaseToken) {
        LockedCreate locked = lockCreateInstanceAndAction(actionId);
        LifecycleActionEntity action = locked == null ? null : locked.action();
        EciInstanceEntity instance = locked == null ? null : locked.instance();
        if (instance == null || !hasLease(action, leaseToken) || !hasText(instance.getCloudInstanceId())) {
            return;
        }
        Instant now = clock.instant();
        requestDelete(instance.getId(), now);
        completeAction(action, now);
        repository.save(action);
    }

    LifecycleActionEntity newDeleteAction(UUID instanceId, Instant notBefore) {
        Instant now = clock.instant();
        LifecycleActionEntity action = new LifecycleActionEntity();
        action.setId(UUID.randomUUID());
        action.setInstanceId(instanceId);
        action.setActionType(LifecycleActionType.DELETE);
        action.setPhase(LifecycleActionPhase.DELETE_REQUEST);
        action.setStatus(LifecycleActionStatus.READY);
        action.setAttemptCount(0);
        action.setNextAttemptAt(notBefore);
        action.setCreatedAt(now);
        action.setUpdatedAt(now);
        return action;
    }

    @Transactional
    public void completeInstanceTermination(UUID instanceId, UUID actionId, UUID leaseToken) {
        EciInstanceEntity instance = lockTaskThenInstance(instanceId);
        LifecycleActionEntity action = repository.findForUpdateById(actionId).orElse(null);
        if (instance == null || !hasLease(action, leaseToken)) {
            return;
        }

        completeAction(action, clock.instant());
        terminateInstance(instance, clock.instant());
        repository.save(action);
        instanceRepository.save(instance);
        onInstanceTerminated(instance);
    }

    @Transactional
    public boolean updateDeleteStep(
            UUID actionId,
            UUID leaseToken,
            Instant claimedUpdatedAt,
            LifecycleActionPhase phase,
            Instant nextAttemptAt,
            int attemptCount,
            String lastError) {
        LifecycleActionEntity action = repository.findForUpdateById(actionId).orElse(null);
        if (!hasLease(action, leaseToken)) {
            return false;
        }

        action.setPhase(phase);
        action.setStatus(LifecycleActionStatus.WAITING);
        action.setAttemptCount(attemptCount);
        boolean requestedWhileInProgress = claimedUpdatedAt != null
                && action.getUpdatedAt().isAfter(claimedUpdatedAt);
        if (requestedWhileInProgress && action.getNextAttemptAt() != null
                && action.getNextAttemptAt().isBefore(nextAttemptAt)) {
            nextAttemptAt = action.getNextAttemptAt();
        }
        action.setNextAttemptAt(nextAttemptAt);
        action.setLastError(truncate(lastError));
        action.setLeaseToken(null);
        action.setLeaseUntil(null);
        Instant now = clock.instant();
        action.setUpdatedAt(action.getUpdatedAt().isAfter(now) ? action.getUpdatedAt() : now);
        repository.save(action);
        return true;
    }

    @Transactional
    public NoCloudDeleteResult resolveDeleteWithoutCloud(
            UUID instanceId,
            UUID actionId,
            UUID leaseToken,
            Instant claimedUpdatedAt,
            Instant nextAttemptAt) {
        EciInstanceEntity instance = lockTaskThenInstance(instanceId);
        LifecycleActionEntity action = repository.findForUpdateById(actionId).orElse(null);
        if (instance == null || !hasLease(action, leaseToken)) {
            return NoCloudDeleteResult.LOST_LEASE;
        }

        ensureLegacyCreateOutcomeMarker(instance, clock.instant());
        LifecycleActionEntity createAction = repository.findForUpdateByInstanceIdAndActionType(
                instanceId, LifecycleActionType.CREATE).orElse(null);
        if (createAction == null && instance.getStatus() == InstanceStatus.TERMINATED) {
            completeAction(action, clock.instant());
            repository.save(action);
            onInstanceTerminated(instance);
            return NoCloudDeleteResult.TERMINATED;
        }
        if (createAction != null && (createAction.getStatus() == LifecycleActionStatus.FAILED
                || createAction.getStatus() == LifecycleActionStatus.CANCELLED)) {
            completeAction(action, clock.instant());
            terminateInstance(instance, clock.instant());
            repository.save(action);
            instanceRepository.save(instance);
            onInstanceTerminated(instance);
            return NoCloudDeleteResult.TERMINATED;
        }

        if (createAction != null
                && createAction.getPhase() == LifecycleActionPhase.CREATE_REQUEST
                && createAction.getAttemptCount() == 0
                && createAction.getStatus() != LifecycleActionStatus.IN_PROGRESS) {
            createAction.setStatus(LifecycleActionStatus.CANCELLED);
            createAction.setLeaseToken(null);
            createAction.setLeaseUntil(null);
            createAction.setCompletedAt(clock.instant());
            createAction.setUpdatedAt(clock.instant());
            repository.save(createAction);
            completeAction(action, clock.instant());
            terminateInstance(instance, clock.instant());
            repository.save(action);
            instanceRepository.save(instance);
            onInstanceTerminated(instance);
            return NoCloudDeleteResult.TERMINATED;
        }

        boolean requestedWhileInProgress = claimedUpdatedAt != null
                && action.getUpdatedAt().isAfter(claimedUpdatedAt);
        if (requestedWhileInProgress && action.getNextAttemptAt() != null
                && action.getNextAttemptAt().isBefore(nextAttemptAt)) {
            nextAttemptAt = action.getNextAttemptAt();
        }
        action.setStatus(LifecycleActionStatus.WAITING);
        action.setNextAttemptAt(nextAttemptAt);
        action.setLeaseToken(null);
        action.setLeaseUntil(null);
        action.setUpdatedAt(clock.instant());
        repository.save(action);
        return NoCloudDeleteResult.WAITING;
    }

    @Transactional
    public Optional<ClaimedLifecycleAction> claim(UUID actionId, Instant now) {
        UUID leaseToken = UUID.randomUUID();
        Instant leaseUntil = now.plusSeconds(properties.getLifecycleAction().getLeaseSec());
        int claimed = repository.claim(actionId, leaseToken, now, leaseUntil,
                CLAIMABLE_STATUSES, LifecycleActionStatus.IN_PROGRESS);
        if (claimed != 1) {
            return Optional.empty();
        }

        return repository.findById(actionId).map(action -> new ClaimedLifecycleAction(
                action.getId(),
                action.getInstanceId(),
                action.getActionType(),
                action.getPhase(),
                action.getLeaseToken()));
    }

    @Transactional
    public void releaseAfterDispatchFailure(
            UUID actionId,
            UUID leaseToken,
            Instant nextAttemptAt,
            String error) {
        int released = repository.releaseExpiredLeases(actionId, leaseToken, nextAttemptAt,
                clock.instant(), LifecycleActionStatus.WAITING, LifecycleActionStatus.IN_PROGRESS);
        if (released != 1) {
            return;
        }

        repository.findById(actionId).ifPresent(action -> {
            action.setLastError(truncate(error));
            repository.save(action);
        });
    }

    @Transactional
    public int recoverExpiredLeases(Instant now) {
        List<UUID> dueIds = repository.findDueIds(now, ACTION_TYPES, CLAIMABLE_STATUSES,
                LifecycleActionStatus.IN_PROGRESS, Pageable.unpaged());
        int recovered = 0;
        for (UUID actionId : dueIds) {
            LifecycleActionEntity action = repository.findById(actionId).orElse(null);
            if (action == null
                    || action.getStatus() != LifecycleActionStatus.IN_PROGRESS
                    || action.getLeaseToken() == null
                    || action.getLeaseUntil() == null
                    || action.getLeaseUntil().isAfter(now)) {
                continue;
            }

            recovered += repository.releaseExpiredLeases(actionId, action.getLeaseToken(),
                    action.getNextAttemptAt(), now,
                    LifecycleActionStatus.WAITING, LifecycleActionStatus.IN_PROGRESS);
        }
        return recovered;
    }

    private static String truncate(String error) {
        if (error == null || error.length() <= LAST_ERROR_MAX_LENGTH) {
            return error;
        }
        return error.substring(0, LAST_ERROR_MAX_LENGTH);
    }

    private EciInstanceEntity lockTaskThenInstance(UUID instanceId) {
        EciInstanceEntity observed = instanceRepository.findById(instanceId).orElse(null);
        if (observed == null) {
            return null;
        }
        if (observed.getTaskId() == null) {
            return instanceRepository.findForUpdateById(instanceId).orElse(null);
        }
        taskService.lockForDeleteCompletion(observed.getTaskId());
        return instanceRepository.findForUpdateById(instanceId).orElse(null);
    }

    private void onInstanceTerminated(EciInstanceEntity instance) {
        if (instance.getTaskId() != null) {
            taskService.onInstanceTerminated(instance.getTaskId(), instance.getCloudStatus());
        }
    }

    private void failBusinessInstance(EciInstanceEntity instance, String reason, Instant now) {
        failBusinessInstance(instance, reason, null, now);
    }

    private void failBusinessInstance(EciInstanceEntity instance, String reason, String logTail, Instant now) {
        if (instance.getStatus() == InstanceStatus.TERMINATED || instance.getStatus() == InstanceStatus.STOPPING) {
            return;
        }
        instance.setStatus(InstanceStatus.FAILED);
        instance.setFailureReason(truncate(reason));
        instance.setUpdatedAt(now);
        if (instance.getTaskId() != null) {
            taskService.onInstanceFailed(instance.getTaskId(), instance.getFailureReason(), logTail);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** Every CREATE mutation which also touches the instance locks in this order. */
    private LockedCreate lockCreateInstanceAndAction(UUID actionId) {
        LifecycleActionEntity observed = repository.findById(actionId).orElse(null);
        if (observed == null) {
            return null;
        }
        EciInstanceEntity instance = instanceRepository.findForUpdateById(observed.getInstanceId()).orElse(null);
        LifecycleActionEntity action = repository.findForUpdateById(actionId).orElse(null);
        if (instance == null || action == null || !instance.getId().equals(action.getInstanceId())) {
            return null;
        }
        return new LockedCreate(instance, action);
    }

    private record LockedCreate(EciInstanceEntity instance, LifecycleActionEntity action) {
    }

    private static boolean hasLease(LifecycleActionEntity action, UUID leaseToken) {
        return action != null
                && action.getStatus() == LifecycleActionStatus.IN_PROGRESS
                && leaseToken != null
                && leaseToken.equals(action.getLeaseToken());
    }

    private static void completeAction(LifecycleActionEntity action, Instant now) {
        action.setStatus(LifecycleActionStatus.SUCCEEDED);
        action.setLeaseToken(null);
        action.setLeaseUntil(null);
        action.setLastError(null);
        action.setCompletedAt(now);
        action.setUpdatedAt(now);
    }

    private static void cancelCreateAction(LifecycleActionEntity action, Instant now) {
        action.setStatus(LifecycleActionStatus.CANCELLED);
        action.setLeaseToken(null);
        action.setLeaseUntil(null);
        action.setCompletedAt(now);
        action.setUpdatedAt(now);
    }

    private static void terminateInstance(EciInstanceEntity instance, Instant now) {
        instance.setStatus(InstanceStatus.TERMINATED);
        if (!"Succeeded".equalsIgnoreCase(instance.getCloudStatus())) {
            instance.setCloudStatus("Deleted");
        }
        instance.setFailureReason(null);
        instance.setUpdatedAt(now);
    }
}
