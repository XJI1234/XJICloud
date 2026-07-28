package com.xjicloud.eci.lifecycle;

import com.xjicloud.eci.instance.CompensationOutcome;
import com.xjicloud.eci.instance.EciInstanceService;
import com.xjicloud.eci.instance.InstanceStatus;
import com.xjicloud.eci.instance.dto.InstanceResponse;
import com.xjicloud.eci.task.EciTaskEntity;
import com.xjicloud.eci.task.EciTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 启动补偿：进程崩溃可能留下 CREATING/STOPPING；启动时扫一遍并对齐或续跑异步任务。
 * 随后扫描非终态任务：明确失败/资源已清 → FAILED，其余交由 {@link TaskLifecycleWatcher} 续轮询。
 * <p>
 * Demo 假定单实例部署；多节点 HA 并发补偿需分布式锁，本阶段不做。
 * 本 Runner 失败只记日志，不阻断应用启动。
 */
@Component
public class StartupCompensationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupCompensationRunner.class);

    private static final String ORPHAN_TERMINATED_REASON = "进程中断或资源已清理";
    private static final String MISSING_INSTANCE_REASON = "关联实例不存在";

    private final EciInstanceService instanceService;
    private final EciTaskService taskService;

    public StartupCompensationRunner(EciInstanceService instanceService, EciTaskService taskService) {
        this.instanceService = instanceService;
        this.taskService = taskService;
    }

    @Override
    public void run(ApplicationArguments args) {
        compensateInstances();
        compensateTasks();
    }

    private void compensateInstances() {
        try {
            List<UUID> ids = instanceService.listIdsForStartupCompensation();
            int resumedCreate = 0;
            int resumedDelete = 0;
            int failedLocal = 0;
            int terminatedLocal = 0;
            int refreshedOnly = 0;
            int skipped = 0;

            for (UUID id : ids) {
                try {
                    CompensationOutcome outcome = instanceService.compensateOne(id);
                    switch (outcome) {
                        case RESUMED_CREATE -> resumedCreate++;
                        case RESUMED_DELETE -> resumedDelete++;
                        case FAILED_LOCAL -> failedLocal++;
                        case TERMINATED_LOCAL -> terminatedLocal++;
                        case REFRESHED_ONLY -> refreshedOnly++;
                        case SKIPPED -> skipped++;
                    }
                } catch (Exception e) {
                    log.warn("startup compensation failed for instance id={}", id, e);
                }
            }

            log.info(
                    "startup compensation done scanned={} resumedCreate={} resumedDelete={} "
                            + "failedLocal={} terminatedLocal={} refreshedOnly={} skipped={}",
                    ids.size(),
                    resumedCreate,
                    resumedDelete,
                    failedLocal,
                    terminatedLocal,
                    refreshedOnly,
                    skipped);
        } catch (Exception e) {
            log.error("startup compensation aborted", e);
        }
    }

    /**
     * 策略（写死）：有明确失败 → FAILED；实例已 TERMINATED → FAILED；其余续轮询/续建。
     */
    private void compensateTasks() {
        try {
            List<EciTaskEntity> tasks = taskService.findNonTerminalForCompensation();
            int failed = 0;
            int deferred = 0;

            for (EciTaskEntity task : tasks) {
                try {
                    if (compensateOneTask(task)) {
                        failed++;
                    } else {
                        deferred++;
                    }
                } catch (Exception e) {
                    log.warn("startup task compensation failed taskId={}", task.getId(), e);
                }
            }

            log.info("startup task compensation done scanned={} failed={} deferredToWatcher={}",
                    tasks.size(), failed, deferred);
        } catch (Exception e) {
            log.error("startup task compensation aborted", e);
        }
    }

    /**
     * @return true 若已将任务标为 FAILED
     */
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
        } catch (Exception e) {
            taskService.onInstanceFailed(taskId, MISSING_INSTANCE_REASON);
            return true;
        }

        if (instance.status() == InstanceStatus.FAILED) {
            String reason = instance.failureReason() != null ? instance.failureReason() : "实例失败";
            taskService.onInstanceFailed(taskId, reason);
            return true;
        }

        if (instance.status() == InstanceStatus.TERMINATED) {
            taskService.onInstanceFailed(taskId, ORPHAN_TERMINATED_REASON);
            return true;
        }

        // CREATING / RUNNING / STOPPING 等：实例补偿已续建/续删，任务交给 Watcher
        return false;
    }
}
