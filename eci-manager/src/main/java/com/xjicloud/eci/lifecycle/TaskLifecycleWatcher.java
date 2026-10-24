package com.xjicloud.eci.lifecycle;

import com.xjicloud.eci.config.EciProperties;
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
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * 任务终态轮询：对 STARTING/RUNNING 任务按云侧状态或 maxRuntimeSec 触发删除并回写任务。
 * <p>
 * SUCCEEDED 仅在关联实例已 TERMINATED 后落库；删除失败不得静默标成功。
 * Failed / TIMEOUT：先抓容器日志尾部落库，可选宽限后再删实例。
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
    private final EciProperties properties;
    private final Clock clock;

    public TaskLifecycleWatcher(
            EciTaskService taskService,
            EciInstanceService instanceService,
            EciProvider eciProvider,
            EciProperties properties,
            Clock clock) {
        this.taskService = taskService;
        this.instanceService = instanceService;
        this.eciProvider = eciProvider;
        this.properties = properties;
        this.clock = clock;
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
            return;
        }

        InstanceResponse instance;
        try {
            instance = instanceService.get(instanceId);
        } catch (Exception e) {
            log.warn("taskId={} instanceId={} lookup failed, marking task FAILED",
                    taskId, instanceId, e);
            taskService.onInstanceFailed(taskId, MISSING_INSTANCE_REASON);
            return;
        }

        String cloudInstanceId = instance.cloudInstanceId();
        log.debug("task poll taskId={} instanceId={} cloudInstanceId={} taskStatus={} instanceStatus={}",
                taskId, instanceId, cloudInstanceId, task.getStatus(), instance.status());

        boolean cloudSucceeded = hasCloudSucceeded(task, instance);
        if (isTimedOut(task, clock.instant()) && !cloudSucceeded) {
            log.info("task timeout taskId={} instanceId={} cloudInstanceId={} maxRuntimeSec={}",
                    taskId, instanceId, cloudInstanceId, task.getMaxRuntimeSec());
            failPersistThenDelete(taskId, task, instance, cloudInstanceId, TIMEOUT_REASON);
            return;
        }

        if (instance.status() == InstanceStatus.FAILED) {
            if (cloudSucceeded) {
                instanceService.deleteForTaskCancel(instanceId);
                return;
            }
            String reason = StringUtils.hasText(instance.failureReason())
                    ? instance.failureReason()
                    : "实例失败";
            failPersistThenDelete(taskId, task, instance, cloudInstanceId, reason);
            return;
        }

        if (instance.status() == InstanceStatus.TERMINATED) {
            if (cloudSucceeded) {
                taskService.markSucceeded(taskId);
            } else {
                taskService.onInstanceFailed(taskId, ORPHAN_TERMINATED_REASON);
            }
            return;
        }

        if (instance.status() == InstanceStatus.STOPPING) {
            // 持久化 DELETE action 负责收敛实例终态。
            return;
        }

        if (!StringUtils.hasText(cloudInstanceId)) {
            // 仍在创建、尚无 cloudId：交给实例创建轮询
            return;
        }

        CloudInstanceView view;
        try {
            view = eciProvider.describe(cloudInstanceId, instanceService.getRegionId(instanceId));
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
            // 同事务落库 Succeeded 证据并受理删除；删除完成后再依持久证据终态化。
            try {
                instanceService.recordCloudSucceededAndDeleteForTask(instanceId);
            } catch (Exception e) {
                log.warn("succeeded cleanup failed taskId={} instanceId={}", taskId, instanceId, e);
            }
            instanceService.recordProviderStatus(instanceId, view.status(), view.statusDetail());
            return;
        }

        if (view.isFailed()) {
            if (!ignoreCloudFailed) {
                String reason = StringUtils.hasText(view.statusDetail())
                        ? view.statusDetail()
                        : "云侧状态: " + view.status();
                failPersistThenDelete(taskId, task, instance, cloudInstanceId, reason);
            }
            instanceService.recordProviderStatus(instanceId, view.status(), view.statusDetail());
            // OnFailure/Always：云侧 Failed 可能随后重启，等待 Succeeded / 超时 / cancel，勿标 RUNNING
            return;
        }

        // 仅云侧 Running 才推进 STARTING→RUNNING；Always 下的 Succeeded 等终态不假装 Running
        if (view.isRunning()) {
            taskService.onInstanceRunning(taskId);
        }
        instanceService.recordProviderStatus(instanceId, view.status(), view.statusDetail());
    }

    /**
     * 硬约束顺序：fetchContainerLog → 落库 FAILED(+logTail) → 可选 grace → ensureDelete。
     * <p>
     * 抓取行数与宽限时间优先用任务快照；旧任务两列为 null 时才回退当前配置。
     */
    private void failPersistThenDelete(
            UUID taskId, EciTaskEntity task, InstanceResponse instance, String cloudInstanceId, String reason) {
        int tailLines = task.getFailureLogTailLines() != null
                ? task.getFailureLogTailLines()
                : properties.getFailureLogTailLines();
        String logTail = fetchLogSafe(cloudInstanceId, tailLines,
                instanceService.getRegionId(instance.id()));
        taskService.onInstanceFailed(taskId, reason, logTail);
        Integer snapshotGraceSec = task.getFailureLogGraceSec();
        int graceSec = Math.max(0,
                snapshotGraceSec != null ? snapshotGraceSec : properties.getFailureLogGraceSec());
        instanceService.deleteForTaskCancel(instance.id(), clock.instant().plusSeconds(graceSec));
    }

    private String fetchLogSafe(String cloudInstanceId, int tailLines, String regionId) {
        if (!StringUtils.hasText(cloudInstanceId)) {
            return null;
        }
        try {
            String logTail = eciProvider.fetchContainerLog(cloudInstanceId, tailLines, regionId);
            return StringUtils.hasText(logTail) ? logTail : null;
        } catch (Exception e) {
            log.warn("fetchContainerLog failed cloudInstanceId={}: {}", cloudInstanceId, e.getMessage());
            return null;
        }
    }

    /**
     * maxRuntimeSec 从实际开始运行（startedAt）起算；STARTING/排队等待不计超时。
     */
    private static boolean isTimedOut(EciTaskEntity task, Instant now) {
        Instant startedAt = task.getStartedAt();
        if (startedAt == null) {
            return false;
        }
        int maxRuntimeSec = task.getMaxRuntimeSec();
        if (maxRuntimeSec <= 0) {
            return false;
        }
        long elapsedSec = now.getEpochSecond() - startedAt.getEpochSecond();
        return elapsedSec > maxRuntimeSec;
    }

    private boolean hasCloudSucceeded(EciTaskEntity task, InstanceResponse instance) {
        return TaskSuccessEvidence.isSatisfied(
                task, instance, instanceService.hasCloudSucceeded(instance.id()));
    }

}
