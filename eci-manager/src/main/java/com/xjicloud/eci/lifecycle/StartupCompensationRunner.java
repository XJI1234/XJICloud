package com.xjicloud.eci.lifecycle;

import com.xjicloud.eci.instance.EciInstanceService;
import com.xjicloud.eci.instance.InstanceStatus;
import com.xjicloud.eci.instance.dto.InstanceResponse;
import com.xjicloud.eci.lifecycle.action.LifecycleActionService;
import com.xjicloud.eci.task.EciTaskEntity;
import com.xjicloud.eci.task.EciTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Startup recovery for persisted actions, legacy records, and task evidence. */
@Component
public class StartupCompensationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupCompensationRunner.class);

    private static final String ORPHAN_TERMINATED_REASON = "进程中断或资源已清理";
    private static final String MISSING_INSTANCE_REASON = "关联实例不存在";

    private final EciInstanceService instanceService;
    private final EciTaskService taskService;
    private final LifecycleActionService lifecycleActionService;
    private final Clock clock;

    public StartupCompensationRunner(
            EciInstanceService instanceService,
            EciTaskService taskService,
            LifecycleActionService lifecycleActionService,
            Clock clock) {
        this.instanceService = instanceService;
        this.taskService = taskService;
        this.lifecycleActionService = lifecycleActionService;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        recoverExpiredLeases();
        compensateLegacyInstances();
        migrateLegacyCancelledTasks();
        compensateTasks();
    }

    private void recoverExpiredLeases() {
        try {
            int recovered = lifecycleActionService.recoverExpiredLeases(clock.instant());
            log.info("startup lifecycle lease recovery done recovered={}", recovered);
        } catch (RuntimeException e) {
            log.warn("startup lifecycle lease recovery failed errorType={}", e.getClass().getSimpleName());
        }
    }

    private void compensateLegacyInstances() {
        List<UUID> ids;
        try {
            ids = instanceService.findCandidateInstanceIds();
        } catch (RuntimeException e) {
            log.warn("startup lifecycle candidate scan failed errorType={}", e.getClass().getSimpleName());
            return;
        }

        for (UUID id : ids) {
            try {
                instanceService.ensureLegacyActionsForInstance(id);
            } catch (RuntimeException e) {
                log.warn("startup lifecycle compensation failed instanceId={} errorType={}",
                        id, e.getClass().getSimpleName());
            }
        }
        log.info("startup lifecycle compensation done scanned={}", ids.size());
    }

    private void migrateLegacyCancelledTasks() {
        List<UUID> ids;
        try {
            ids = instanceService.findLegacyCancelledTaskIds();
        } catch (RuntimeException e) {
            log.warn("startup legacy cancellation scan failed errorType={}", e.getClass().getSimpleName());
            return;
        }

        for (UUID id : ids) {
            try {
                instanceService.migrateLegacyCancelledTask(id);
            } catch (RuntimeException e) {
                log.warn("startup legacy cancellation migration failed taskId={} errorType={}",
                        id, e.getClass().getSimpleName());
            }
        }
        log.info("startup legacy cancellation migration done scanned={}", ids.size());
    }

    private void compensateTasks() {
        List<EciTaskEntity> tasks;
        try {
            tasks = taskService.findNonTerminalForCompensation();
        } catch (RuntimeException e) {
            log.warn("startup task compensation scan failed errorType={}", e.getClass().getSimpleName());
            return;
        }

        int failed = 0;
        int deferred = 0;
        for (EciTaskEntity task : tasks) {
            try {
                if (compensateOneTask(task)) {
                    failed++;
                } else {
                    deferred++;
                }
            } catch (RuntimeException e) {
                log.warn("startup task compensation failed taskId={} errorType={}",
                        task.getId(), e.getClass().getSimpleName());
            }
        }

        log.info("startup task compensation done scanned={} failed={} deferredToWatcher={}",
                tasks.size(), failed, deferred);
    }

    /** @return true when this startup pass terminalized the task. */
    private boolean compensateOneTask(EciTaskEntity task) {
        UUID taskId = task.getId();
        UUID instanceId = task.getInstanceId();
        if (instanceId == null) {
            taskService.onInstanceFailed(taskId, MISSING_INSTANCE_REASON);
            return true;
        }

        InstanceResponse instance;
        try {
            instance = instanceService.get(instanceId);
        } catch (RuntimeException e) {
            taskService.onInstanceFailed(taskId, MISSING_INSTANCE_REASON);
            return true;
        }

        if (instance.status() == InstanceStatus.FAILED) {
            String reason = instance.failureReason() != null ? instance.failureReason() : "实例失败";
            taskService.onInstanceFailed(taskId, reason);
            return true;
        }

        if (instance.status() == InstanceStatus.TERMINATED) {
            if (instance.cloudStatus() != null && "Succeeded".equalsIgnoreCase(instance.cloudStatus())) {
                taskService.markSucceeded(taskId);
            } else {
                taskService.onInstanceFailed(taskId, ORPHAN_TERMINATED_REASON);
            }
            return true;
        }

        return false;
    }
}
