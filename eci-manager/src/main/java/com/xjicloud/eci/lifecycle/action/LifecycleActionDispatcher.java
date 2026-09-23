package com.xjicloud.eci.lifecycle.action;

import com.xjicloud.eci.config.EciProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

@Component
public class LifecycleActionDispatcher {

    private static final Logger log = LoggerFactory.getLogger(LifecycleActionDispatcher.class);
    private static final Set<LifecycleActionType> ENABLED_TYPES =
            Set.of(LifecycleActionType.CREATE, LifecycleActionType.DELETE);
    private static final Set<LifecycleActionStatus> CLAIMABLE_STATUSES =
            Set.of(LifecycleActionStatus.READY, LifecycleActionStatus.WAITING);

    private final LifecycleActionRepository repository;
    private final LifecycleActionService actionService;
    private final LifecycleActionProcessor processor;
    private final TaskExecutor executor;
    private final EciProperties properties;
    private final Clock clock;

    public LifecycleActionDispatcher(
            LifecycleActionRepository repository,
            LifecycleActionService actionService,
            LifecycleActionProcessor processor,
            TaskExecutor eciLifecycleExecutor,
            EciProperties properties,
            Clock clock) {
        this.repository = repository;
        this.actionService = actionService;
        this.processor = processor;
        this.executor = eciLifecycleExecutor;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${eci.lifecycle-action.dispatch-interval-ms:1000}")
    public void scheduledDispatch() {
        try {
            dispatchOnce();
        } catch (Exception exception) {
            log.error("lifecycle action dispatch scan failed", exception);
        }
    }

    public void dispatchOnce() {
        Instant now = clock.instant();
        List<UUID> dueIds;
        try {
            dueIds = repository.findDueIds(now, ENABLED_TYPES, CLAIMABLE_STATUSES,
                    LifecycleActionStatus.IN_PROGRESS,
                    PageRequest.of(0, properties.getLifecycleAction().getBatchSize()));
        } catch (Exception exception) {
            log.error("lifecycle action dispatch query failed", exception);
            return;
        }

        for (UUID actionId : dueIds) {
            try {
                actionService.claim(actionId, now).ifPresent(claimed -> submit(claimed, now));
            } catch (Exception exception) {
                log.error("lifecycle action claim failed actionId={}", actionId, exception);
            }
        }
    }

    private void submit(ClaimedLifecycleAction claimed, Instant now) {
        try {
            executor.execute(() -> processor.process(claimed.actionId(), claimed.leaseToken()));
        } catch (RejectedExecutionException exception) {
            release(claimed, now, "executor rejected lifecycle action dispatch");
        } catch (Exception exception) {
            log.error("lifecycle action submit failed actionId={}", claimed.actionId(), exception);
            release(claimed, now, "lifecycle action dispatch failed: " + exception.getMessage());
        }
    }

    private void release(ClaimedLifecycleAction claimed, Instant now, String error) {
        try {
            actionService.releaseAfterDispatchFailure(claimed.actionId(), claimed.leaseToken(),
                    now.plusSeconds(1), error);
        } catch (Exception exception) {
            log.error("lifecycle action dispatch lease release failed actionId={}",
                    claimed.actionId(), exception);
        }
    }
}
