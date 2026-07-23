package com.xjicloud.eci.lifecycle;

import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.instance.EciInstanceEntity;
import com.xjicloud.eci.instance.EciInstanceRepository;
import com.xjicloud.eci.instance.InstanceStatus;
import com.xjicloud.eci.provider.CloudInstanceView;
import com.xjicloud.eci.provider.CreateSpec;
import com.xjicloud.eci.provider.EciProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class AsyncLifecycleWorker {

    private static final Logger log = LoggerFactory.getLogger(AsyncLifecycleWorker.class);

    private final EciInstanceRepository repository;
    private final EciProvider eciProvider;
    private final EciProperties properties;

    public AsyncLifecycleWorker(
            EciInstanceRepository repository,
            EciProvider eciProvider,
            EciProperties properties) {
        this.repository = repository;
        this.eciProvider = eciProvider;
        this.properties = properties;
    }

    @Async("eciLifecycleExecutor")
    public void scheduleCreate(UUID localId, CreateSpec spec) {
        long start = System.currentTimeMillis();
        try {
            String cloudId = eciProvider.create(spec);
            updateAfterCreateAccepted(localId, cloudId);
            pollUntilRunning(localId, cloudId);
            log.info("create lifecycle done localId={} cloudInstanceId={} elapsedMs={}",
                    localId, cloudId, System.currentTimeMillis() - start);
        } catch (Exception e) {
            markFailed(localId, e.getMessage());
            log.error("create lifecycle failed localId={} elapsedMs={}",
                    localId, System.currentTimeMillis() - start, e);
        }
    }

    @Async("eciLifecycleExecutor")
    public void scheduleDelete(UUID localId) {
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
            } else {
                markTerminated(localId);
            }
            log.info("delete lifecycle done localId={} cloudInstanceId={} elapsedMs={}",
                    localId, cloudId, System.currentTimeMillis() - start);
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

    private void pollUntilRunning(UUID localId, String cloudId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + properties.getCreateTimeoutSec() * 1000L;
        while (System.currentTimeMillis() < deadline) {
            CloudInstanceView view = eciProvider.describe(cloudId);
            applyCloudStatus(localId, view);
            if (view.isRunning()) {
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
        long deadline = System.currentTimeMillis() + properties.getCreateTimeoutSec() * 1000L;
        while (System.currentTimeMillis() < deadline) {
            CloudInstanceView view = eciProvider.describe(cloudId);
            applyCloudStatus(localId, view);
            if (view.isTerminatedOrMissing()) {
                markTerminated(localId);
                return;
            }
            Thread.sleep(properties.getPollIntervalMs());
        }
        // Delete API already accepted; treat timeout after delete as terminated for demo simplicity
        // if describe still returns something transient
        CloudInstanceView last = eciProvider.describe(cloudId);
        if (last.isTerminatedOrMissing()) {
            markTerminated(localId);
        } else {
            markFailed(localId, "等待删除完成超时（" + properties.getCreateTimeoutSec() + "s）");
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
        repository.findById(localId).ifPresent(entity -> {
            if (entity.getStatus() == InstanceStatus.CREATING || entity.getStatus() == InstanceStatus.FAILED) {
                entity.setStatus(InstanceStatus.RUNNING);
                entity.setCloudStatus(cloudStatus);
                entity.setFailureReason(null);
                entity.setUpdatedAt(Instant.now());
                repository.save(entity);
            }
        });
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
        repository.findById(localId).ifPresent(entity -> {
            if (entity.getStatus() == InstanceStatus.TERMINATED) {
                return;
            }
            entity.setStatus(InstanceStatus.FAILED);
            entity.setFailureReason(truncate(reason));
            entity.setUpdatedAt(Instant.now());
            repository.save(entity);
        });
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return "unknown error";
        }
        return reason.length() > 1000 ? reason.substring(0, 1000) : reason;
    }
}
