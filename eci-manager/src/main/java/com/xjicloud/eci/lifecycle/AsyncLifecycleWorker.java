package com.xjicloud.eci.lifecycle;

import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.instance.EciInstanceEntity;
import com.xjicloud.eci.instance.EciInstanceRepository;
import com.xjicloud.eci.instance.InstanceStatus;
import com.xjicloud.eci.provider.CloudInstanceView;
import com.xjicloud.eci.provider.CreateSpec;
import com.xjicloud.eci.provider.EciProvider;
import com.xjicloud.eci.task.EciTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * 异步生命周期执行器：在独立线程中完成「调云 + 轮询」，避免阻塞 HTTP。
 */
@Component
public class AsyncLifecycleWorker {

    private static final Logger log = LoggerFactory.getLogger(AsyncLifecycleWorker.class);

    private final EciInstanceRepository repository;
    private final EciProvider eciProvider;
    private final EciProperties properties;
    private final EciTaskService taskService;

    public AsyncLifecycleWorker(
            EciInstanceRepository repository,
            EciProvider eciProvider,
            EciProperties properties,
            @Lazy EciTaskService taskService) {
        this.repository = repository;
        this.eciProvider = eciProvider;
        this.properties = properties;
        this.taskService = taskService;
    }

    /**
     * 异步创建：Create → 写入 cloudInstanceId → 轮询至 Running / 失败 / 超时。
     * <p>
     * 若任务取消已将实例置为 STOPPING/TERMINATED，则改为删云，避免推成 RUNNING。
     */
    @Async("eciLifecycleExecutor")
    public void scheduleCreate(UUID localId, CreateSpec spec) {
        long start = System.currentTimeMillis();
        try {
            String cloudId = eciProvider.create(spec);
            updateAfterCreateAccepted(localId, cloudId);
            if (isCreateAborted(localId)) {
                runDeleteLifecycle(localId);
                log.info("create aborted after accept (cancel race) localId={} cloudInstanceId={} elapsedMs={}",
                        localId, cloudId, System.currentTimeMillis() - start);
                return;
            }
            pollUntilRunning(localId, cloudId);
            log.info("create lifecycle done localId={} cloudInstanceId={} elapsedMs={}",
                    localId, cloudId, System.currentTimeMillis() - start);
        } catch (Exception e) {
            markFailed(localId, e.getMessage());
            log.error("create lifecycle failed localId={} elapsedMs={}",
                    localId, System.currentTimeMillis() - start, e);
        }
    }

    /**
     * 异步删除：调用云 Delete 后轮询直至消失；无 cloudId 则直接本地 TERMINATED。
     */
    @Async("eciLifecycleExecutor")
    public void scheduleDelete(UUID localId) {
        runDeleteLifecycle(localId);
    }

    /**
     * 启动补偿：仅续跑创建轮询（不再调云 Create），避免重复建实例。
     */
    @Async("eciLifecycleExecutor")
    public void resumeCreatePoll(UUID localId, String cloudId) {
        long start = System.currentTimeMillis();
        try {
            pollUntilRunning(localId, cloudId);
            log.info("resume create poll done localId={} cloudInstanceId={} elapsedMs={}",
                    localId, cloudId, System.currentTimeMillis() - start);
        } catch (Exception e) {
            markFailed(localId, e.getMessage());
            log.error("resume create poll failed localId={} elapsedMs={}",
                    localId, System.currentTimeMillis() - start, e);
        }
    }

    /**
     * 启动补偿：续跑删除（Delete 幂等 + 轮询消失），语义同 {@link #scheduleDelete}。
     */
    @Async("eciLifecycleExecutor")
    public void resumeDelete(UUID localId) {
        runDeleteLifecycle(localId);
    }

    private void runDeleteLifecycle(UUID localId) {
        long start = System.currentTimeMillis();
        try {
            EciInstanceEntity entity = repository.findById(localId).orElse(null);
            if (entity == null) {
                return;
            }
            String cloudId = entity.getCloudInstanceId();
            if (cloudId != null && !cloudId.isBlank()) {
                eciProvider.delete(cloudId);
                pollUntilDeleted(localId, cloudId);
                log.info("delete lifecycle done localId={} cloudInstanceId={} elapsedMs={}",
                        localId, cloudId, System.currentTimeMillis() - start);
            } else {
                markTerminated(localId);
                log.info("delete lifecycle done localId={} cloudInstanceId={} elapsedMs={}",
                        localId, "(none)", System.currentTimeMillis() - start);
            }
        } catch (Exception e) {
            markFailed(localId, e.getMessage());
            log.error("delete lifecycle failed localId={} elapsedMs={}",
                    localId, System.currentTimeMillis() - start, e);
        }
    }

    @Transactional
    protected void updateAfterCreateAccepted(UUID localId, String cloudId) {
        repository.findById(localId).ifPresent(entity -> {
            entity.setCloudInstanceId(cloudId);
            entity.setCloudStatus("Pending");
            entity.setUpdatedAt(Instant.now());
            repository.save(entity);
        });
    }

    private boolean isCreateAborted(UUID localId) {
        return repository.findById(localId)
                .map(entity -> entity.getStatus() == InstanceStatus.STOPPING
                        || entity.getStatus() == InstanceStatus.TERMINATED)
                .orElse(true);
    }

    private void pollUntilRunning(UUID localId, String cloudId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + properties.getCreateTimeoutSec() * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (isCreateAborted(localId)) {
                runDeleteLifecycle(localId);
                return;
            }
            CloudInstanceView view = eciProvider.describe(cloudId);
            applyCloudStatus(localId, view);
            if (view.isRunning() || view.isSucceeded()) {
                // 短任务可能 Pending → Succeeded 未见 Running；创建成功路径仍 markRunning
                if (isCreateAborted(localId)) {
                    runDeleteLifecycle(localId);
                    return;
                }
                markRunning(localId, view.status());
                return;
            }
            if (view.isFailed()) {
                markFailed(localId, view.statusDetail() != null ? view.statusDetail() : view.status());
                return;
            }
            Thread.sleep(properties.getPollIntervalMs());
        }
        markFailed(localId, "等待 Running 超时（" + properties.getCreateTimeoutSec() + "s）");
    }

    private void pollUntilDeleted(UUID localId, String cloudId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + properties.getDeleteTimeoutSec() * 1000L;
        while (System.currentTimeMillis() < deadline) {
            CloudInstanceView view = eciProvider.describe(cloudId);
            applyCloudStatus(localId, view);
            if (view.isTerminatedOrMissing()) {
                markTerminated(localId);
                return;
            }
            Thread.sleep(properties.getPollIntervalMs());
        }
        // Delete 已受理但 Describe 仍短暂可见时，超时后再判一次
        CloudInstanceView last = eciProvider.describe(cloudId);
        if (last.isTerminatedOrMissing()) {
            markTerminated(localId);
        } else {
            markFailed(localId, "等待删除完成超时（" + properties.getDeleteTimeoutSec() + "s）");
        }
    }

    @Transactional
    protected void applyCloudStatus(UUID localId, CloudInstanceView view) {
        repository.findById(localId).ifPresent(entity -> {
            entity.setCloudStatus(view.status());
            entity.setUpdatedAt(Instant.now());
            repository.save(entity);
        });
    }

    @Transactional
    protected void markRunning(UUID localId, String cloudStatus) {
        UUID[] taskIdHolder = new UUID[1];
        repository.findById(localId).ifPresent(entity -> {
            if (entity.getStatus() == InstanceStatus.CREATING || entity.getStatus() == InstanceStatus.FAILED) {
                entity.setStatus(InstanceStatus.RUNNING);
                entity.setCloudStatus(cloudStatus);
                entity.setFailureReason(null);
                entity.setUpdatedAt(Instant.now());
                repository.save(entity);
                taskIdHolder[0] = entity.getTaskId();
            }
        });
        if (taskIdHolder[0] != null && taskService != null) {
            taskService.onInstanceRunning(taskIdHolder[0]);
        }
    }

    @Transactional
    protected void markTerminated(UUID localId) {
        repository.findById(localId).ifPresent(entity -> {
            entity.setStatus(InstanceStatus.TERMINATED);
            entity.setCloudStatus("Deleted");
            entity.setFailureReason(null);
            entity.setUpdatedAt(Instant.now());
            repository.save(entity);
        });
    }

    @Transactional
    protected void markFailed(UUID localId, String reason) {
        UUID[] taskIdHolder = new UUID[1];
        String[] reasonHolder = new String[1];
        repository.findById(localId).ifPresent(entity -> {
            if (entity.getStatus() == InstanceStatus.TERMINATED
                    || entity.getStatus() == InstanceStatus.STOPPING) {
                return;
            }
            entity.setStatus(InstanceStatus.FAILED);
            entity.setFailureReason(truncate(reason));
            entity.setUpdatedAt(Instant.now());
            repository.save(entity);
            taskIdHolder[0] = entity.getTaskId();
            reasonHolder[0] = entity.getFailureReason();
        });
        if (taskIdHolder[0] != null && taskService != null) {
            taskService.onInstanceFailed(taskIdHolder[0], reasonHolder[0]);
        }
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return "unknown error";
        }
        return reason.length() > 1000 ? reason.substring(0, 1000) : reason;
    }
}
