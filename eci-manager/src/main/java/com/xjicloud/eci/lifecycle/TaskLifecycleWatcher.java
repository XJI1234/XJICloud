package com.xjicloud.eci.lifecycle;

import com.xjicloud.eci.instance.EciInstanceService;
import com.xjicloud.eci.instance.InstanceStatus;
import com.xjicloud.eci.instance.dto.InstanceResponse;
import com.xjicloud.eci.provider.CloudInstanceView;
import com.xjicloud.eci.provider.EciProvider;
import com.xjicloud.eci.task.EciTaskEntity;
import com.xjicloud.eci.task.EciTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务终态轮询：对 STARTING/RUNNING 任务按云侧状态或 maxRuntimeSec 触发删除并回写任务。
 * <p>
 * SUCCEEDED 仅在关联实例已 TERMINATED 后落库；删除失败不得静默标成功。
 */
@Component
public class TaskLifecycleWatcher {

    private static final Logger log = LoggerFactory.getLogger(TaskLifecycleWatcher.class);

    private static final String TIMEOUT_REASON = "TIMEOUT";
    private static final String MISSING_INSTANCE_REASON = "关联实例不存在";
    private static final String ORPHAN_TERMINATED_REASON = "进程中断或资源已清理";

    private final EciTaskService taskService;
    private final EciInstanceService instanceService;
    private final EciProvider eciProvider;

    /** 已见云侧 Succeeded、等待本地实例 TERMINATED 后再 markSucceeded。 */
    private final Set<UUID> pendingSucceeded = ConcurrentHashMap.newKeySet();

    public TaskLifecycleWatcher(
            EciTaskService taskService,
            EciInstanceService instanceService,
            EciProvider eciProvider) {
        this.taskService = taskService;
        this.instanceService = instanceService;
        this.eciProvider = eciProvider;
    }

    @Scheduled(fixedDelayString = "#{${eci.task-poll-interval-sec} * 1000}")
    public void scheduledPoll() {
        try {
            pollOnce();
        } catch (Exception e) {
            log.error("task lifecycle poll aborted", e);
        }
    }

    /** 扫描一轮非终态任务（测试可直接调用）。 */
    public void pollOnce() {
        List<EciTaskEntity> active = taskService.findActiveForWatch();
        for (EciTaskEntity task : active) {
            try {
                pollOne(task);
            } catch (Exception e) {
                log.warn("task lifecycle poll failed taskId={} instanceId={}",
                        task.getId(), task.getInstanceId(), e);
            }
        }
    }

    private void pollOne(EciTaskEntity task) {
        UUID taskId = task.getId();
        UUID instanceId = task.getInstanceId();
        if (instanceId == null) {
            taskService.onInstanceFailed(taskId, MISSING_INSTANCE_REASON);
            pendingSucceeded.remove(taskId);
            return;
        }

        InstanceResponse instance;
        try {
            instance = instanceService.get(instanceId);
        } catch (Exception e) {
            log.warn("taskId={} instanceId={} lookup failed, marking task FAILED",
                    taskId, instanceId, e);
            taskService.onInstanceFailed(taskId, MISSING_INSTANCE_REASON);
            pendingSucceeded.remove(taskId);
            return;
        }

        String cloudInstanceId = instance.cloudInstanceId();
        log.debug("task poll taskId={} instanceId={} cloudInstanceId={} taskStatus={} instanceStatus={}",
                taskId, instanceId, cloudInstanceId, task.getStatus(), instance.status());

        if (isTimedOut(task)) {
            log.info("task timeout taskId={} instanceId={} cloudInstanceId={} maxRuntimeSec={}",
                    taskId, instanceId, cloudInstanceId, task.getMaxRuntimeSec());
            ensureDelete(instance);
            taskService.onInstanceFailed(taskId, TIMEOUT_REASON);
            pendingSucceeded.remove(taskId);
            return;
        }

        if (instance.status() == InstanceStatus.FAILED) {
            String reason = StringUtils.hasText(instance.failureReason())
                    ? instance.failureReason()
                    : "实例失败";
            ensureDelete(instance);
            taskService.onInstanceFailed(taskId, reason);
            pendingSucceeded.remove(taskId);
            return;
        }

        if (instance.status() == InstanceStatus.TERMINATED) {
            if (pendingSucceeded.remove(taskId)) {
                taskService.markSucceeded(taskId);
            } else {
                taskService.onInstanceFailed(taskId, ORPHAN_TERMINATED_REASON);
            }
            return;
        }

        if (instance.status() == InstanceStatus.STOPPING) {
            // 等待异步删完成；若已登记 Succeeded 意图则下轮 TERMINATED 后 SUCCEEDED
            return;
        }

        if (!StringUtils.hasText(cloudInstanceId)) {
            // 仍在创建、尚无 cloudId：交给实例创建轮询
            return;
        }

        CloudInstanceView view;
        try {
            view = eciProvider.describe(cloudInstanceId);
        } catch (Exception e) {
            log.warn("describe failed taskId={} instanceId={} cloudInstanceId={}",
                    taskId, instanceId, cloudInstanceId, e);
            return;
        }

        String restartPolicy = task.getRestartPolicy() != null ? task.getRestartPolicy() : "Never";
        boolean ignoreCloudTerminal = "Always".equalsIgnoreCase(restartPolicy);
        boolean ignoreCloudFailed = ignoreCloudTerminal
                || "OnFailure".equalsIgnoreCase(restartPolicy);

        if (view.isSucceeded() && !ignoreCloudTerminal) {
            pendingSucceeded.add(taskId);
            ensureDelete(instance);
            InstanceResponse after = safeGet(instanceId);
            if (after != null && after.status() == InstanceStatus.TERMINATED) {
                pendingSucceeded.remove(taskId);
                taskService.markSucceeded(taskId);
            }
            return;
        }

        if (view.isFailed()) {
            if (!ignoreCloudFailed) {
                String reason = StringUtils.hasText(view.statusDetail())
                        ? view.statusDetail()
                        : "云侧状态: " + view.status();
                taskService.onInstanceFailed(taskId, reason);
                pendingSucceeded.remove(taskId);
                ensureDelete(instance);
            }
            // OnFailure/Always：云侧 Failed 可能随后重启，等待 Succeeded / 超时 / cancel，勿标 RUNNING
            return;
        }

        // 仅云侧 Running 才推进 STARTING→RUNNING；Always 下的 Succeeded 等终态不假装 Running
        if (view.isRunning()) {
            taskService.onInstanceRunning(taskId);
        }
    }

    private static boolean isTimedOut(EciTaskEntity task) {
        Instant anchor = task.getStartedAt() != null ? task.getStartedAt() : task.getCreatedAt();
        if (anchor == null) {
            return false;
        }
        long elapsedSec = Instant.now().getEpochSecond() - anchor.getEpochSecond();
        return elapsedSec > task.getMaxRuntimeSec();
    }

    private void ensureDelete(InstanceResponse instance) {
        if (instance.status() == InstanceStatus.TERMINATED
                || instance.status() == InstanceStatus.STOPPING) {
            return;
        }
        try {
            instanceService.deleteForTaskCancel(instance.id());
        } catch (Exception e) {
            log.warn("ensure delete failed instanceId={} taskId={}",
                    instance.id(), instance.taskId(), e);
        }
    }

    private InstanceResponse safeGet(UUID instanceId) {
        try {
            return instanceService.get(instanceId);
        } catch (Exception e) {
            return null;
        }
    }
}
