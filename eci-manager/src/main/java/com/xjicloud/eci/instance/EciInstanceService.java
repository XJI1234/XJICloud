package com.xjicloud.eci.instance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.eci.common.BusinessException;
import com.xjicloud.eci.config.CloudResourceConfigService;
import com.xjicloud.eci.config.CloudResourceConfigSnapshot;
import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.instance.dto.CreateInstanceRequest;
import com.xjicloud.eci.instance.dto.InstanceResponse;
import com.xjicloud.eci.lifecycle.action.LifecycleActionService;
import com.xjicloud.eci.provider.CloudInstanceView;
import com.xjicloud.eci.provider.EciProvider;
import com.xjicloud.eci.provider.ShmSize;
import com.xjicloud.eci.task.EciTaskEntity;
import com.xjicloud.eci.task.EciTaskRepository;
import com.xjicloud.eci.task.EciTaskService;
import com.xjicloud.eci.task.TaskStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.Clock;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 实例业务服务：维护本地状态机权威，编排异步创建/删除，并支持按云状态 refresh。
 */
@Service
public class EciInstanceService {

    private static final Set<InstanceStatus> STARTUP_COMPENSATION_STATUSES = EnumSet.of(
            InstanceStatus.CREATING,
            InstanceStatus.STOPPING,
            InstanceStatus.FAILED);
    private static final Set<TaskStatus> LEGACY_CANCELLED_STATUS = Set.of(TaskStatus.CANCELLED);

    private final EciInstanceRepository repository;
    private final EciProperties properties;
    private final EciProvider eciProvider;
    private final LifecycleActionService lifecycleActionService;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final EciInstanceService self;
    /** 任务状态回写；单测可传 null。 */
    private final EciTaskService taskService;
    private final EciTaskRepository taskRepository;
    private final CloudResourceConfigService cloudResourceConfigService;

    @Autowired
    public EciInstanceService(
            EciInstanceRepository repository,
            EciProperties properties,
            EciProvider eciProvider,
            ObjectMapper objectMapper,
            @Lazy EciTaskService taskService,
            LifecycleActionService lifecycleActionService,
            Clock clock,
            EciTaskRepository taskRepository,
            @Lazy EciInstanceService self,
            CloudResourceConfigService cloudResourceConfigService) {
        this.repository = repository;
        this.properties = properties;
        this.eciProvider = eciProvider;
        this.lifecycleActionService = lifecycleActionService;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.taskService = taskService;
        this.taskRepository = taskRepository;
        this.self = self;
        this.cloudResourceConfigService = cloudResourceConfigService;
    }

    public EciInstanceService(
            EciInstanceRepository repository,
            EciProperties properties,
            EciProvider eciProvider,
            ObjectMapper objectMapper,
            EciTaskService taskService,
            LifecycleActionService lifecycleActionService,
            Clock clock,
            EciTaskRepository taskRepository,
            EciInstanceService self) {
        this(repository, properties, eciProvider, objectMapper, taskService,
                lifecycleActionService, clock, taskRepository, self, null);
    }

    /** Retained for the focused service tests that construct this service without Spring. */
    public EciInstanceService(
            EciInstanceRepository repository,
            EciProperties properties,
            EciProvider eciProvider,
            ObjectMapper objectMapper,
            org.springframework.transaction.PlatformTransactionManager ignoredTransactionManager,
            @Lazy EciTaskService taskService,
            @Lazy EciInstanceService ignoredSelf,
            LifecycleActionService lifecycleActionService,
            Clock clock) {
        this(repository, properties, eciProvider, objectMapper, taskService,
                lifecycleActionService, clock, null, ignoredSelf);
    }

    /**
     * 受理创建：同一事务落库 CREATING 和持久化 CREATE 动作，后台 dispatcher 再执行短步骤。
     */
    @Transactional
    public InstanceResponse create(CreateInstanceRequest request) {
        return createInternal(request, null, null);
    }

    /** 任务驱动创建：绑定 {@code taskId}。 */
    @Transactional
    public InstanceResponse createForTask(CreateInstanceRequest request, UUID taskId) {
        if (taskId == null) {
            throw new BusinessException("taskId 不能为空");
        }
        return createInternal(request, taskId, null);
    }

    @Transactional
    public InstanceResponse createForTask(
            CreateInstanceRequest request, UUID taskId, CloudResourceConfigSnapshot configSnapshot) {
        if (taskId == null) {
            throw new BusinessException("taskId 不能为空");
        }
        return createInternal(request, taskId, configSnapshot);
    }

    private InstanceResponse createInternal(
            CreateInstanceRequest request, UUID taskId, CloudResourceConfigSnapshot configSnapshot) {
        if (configSnapshot == null && cloudResourceConfigService != null) {
            configSnapshot = cloudResourceConfigService.snapshot();
        }
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();

        String name = StringUtils.hasText(request.getName()) ? request.getName() : "demo-" + id.toString().substring(0, 8);
        String image = StringUtils.hasText(request.getImage()) ? request.getImage()
                : configSnapshot != null ? configSnapshot.advanced().defaultImage() : properties.getDefaultImage();
        double cpu = request.getCpu() != null ? request.getCpu() : properties.getDefaultCpu();
        double memory = request.getMemory() != null ? request.getMemory() : properties.getDefaultMemory();
        Map<String, String> env = request.getEnv() != null ? new HashMap<>(request.getEnv()) : new HashMap<>();
        String instanceType = StringUtils.hasText(request.getInstanceType()) ? request.getInstanceType() : null;
        Integer gpu = request.getGpu();
        String imageSnapshotId = StringUtils.hasText(request.getImageSnapshotId())
                ? request.getImageSnapshotId().trim()
                : null;
        String defaultShmSize = configSnapshot != null
                ? configSnapshot.advanced().defaultShmSize()
                : properties.getDefaultShmSize();
        String shmSize = resolveShmSize(request.getShmSize(), memory, defaultShmSize);
        String restartPolicy = StringUtils.hasText(request.getRestartPolicy())
                ? request.getRestartPolicy().trim()
                : properties.getDefaultRestartPolicy();
        List<String> command = request.getCommand();
        List<String> args = request.getArgs();

        EciInstanceEntity entity = new EciInstanceEntity();
        entity.setId(id);
        entity.setTaskId(taskId);
        entity.setName(name);
        entity.setImage(image);
        entity.setCpu(cpu);
        entity.setMemory(memory);
        entity.setStatus(InstanceStatus.CREATING);
        entity.setEnvJson(toJson(env));
        entity.setCommandJson(toJson(command));
        entity.setArgsJson(toJson(args));
        entity.setInstanceType(instanceType);
        entity.setGpu(gpu);
        entity.setImageSnapshotId(imageSnapshotId);
        entity.setShmSize(shmSize);
        entity.setRestartPolicy(restartPolicy);
        // 提交时的配置快照随实例固化；排队后才创建的任务不得改用后来的配置
        CloudResourceConfigSnapshot.Network network = configSnapshot != null ? configSnapshot.network() : null;
        entity.setRegionId(network != null ? network.regionId() : properties.getRegionId());
        entity.setVpcId(network != null ? network.vpcId() : properties.getVpcId());
        entity.setVswitchId(network != null ? network.vswitchId() : properties.getVswitchId());
        entity.setSecurityGroupId(network != null ? network.securityGroupId() : properties.getSecurityGroupId());
        entity.setCreateTimeoutSec(configSnapshot != null
                ? configSnapshot.advanced().createTimeoutSec()
                : properties.getCreateTimeoutSec());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        repository.save(entity);
        lifecycleActionService.createCreateAction(id);

        return toResponse(entity);
    }

    /**
     * 受理删除：TERMINATED 幂等、CREATING 中拒绝；其余状态置 STOPPING 并幂等登记持久化 DELETE。
     */
    @Transactional
    public InstanceResponse delete(UUID id) {
        EciInstanceEntity entity = findEntityForUpdate(id);

        if (entity.getStatus() == InstanceStatus.TERMINATED) {
            return toResponse(entity);
        }

        if (entity.getStatus() == InstanceStatus.STOPPING) {
            lifecycleActionService.requestDelete(id, clock.instant());
            return toResponse(entity);
        }

        if (entity.getStatus() == InstanceStatus.CREATING) {
            throw new BusinessException("实例仍在创建中，请稍后再删除", HttpStatus.CONFLICT);
        }

        Instant now = clock.instant();
        lifecycleActionService.requestDelete(id, now);
        return toResponse(entity);
    }

    /**
     * 任务取消专用删除：允许 CREATING；登记持久化 DELETE，由 CREATE 依赖判断何时可本地收敛。
     */
    @Transactional
    public InstanceResponse deleteForTaskCancel(UUID id) {
        return deleteForTaskCancel(id, clock.instant());
    }

    @Transactional
    public InstanceResponse deleteForTaskCancel(UUID id, Instant notBefore) {
        EciInstanceEntity entity = findEntityForUpdate(id);

        if (entity.getStatus() == InstanceStatus.TERMINATED) {
            return toResponse(entity);
        }

        lifecycleActionService.requestDelete(id, notBefore);
        return toResponse(entity);
    }

    /**
     * 任务轮询见云侧 Succeeded：落库证据并受理删除（同事务）。
     * <p>
     * 仅 CREATING/RUNNING 可写入 Succeeded 并删；STOPPING 仅补齐证据；TERMINATED/FAILED 不改写。
     * 删除抛错时整段回滚，避免「已记 Succeeded 但未删」的半截状态。
     */
    @Transactional
    public void recordCloudSucceededAndDeleteForTask(UUID id) {
        EciInstanceEntity entity = findEntityForUpdate(id);
        InstanceStatus status = entity.getStatus();

        if (status == InstanceStatus.TERMINATED || status == InstanceStatus.FAILED) {
            return;
        }

        if (status == InstanceStatus.STOPPING) {
            if (!entity.hasCloudSucceeded()) {
                entity.recordCloudObservation("Succeeded", entity.getStatusReason());
                entity.setUpdatedAt(clock.instant());
                repository.save(entity);
            }
            lifecycleActionService.requestDelete(id, clock.instant());
            return;
        }

        if (status != InstanceStatus.CREATING && status != InstanceStatus.RUNNING) {
            throw new BusinessException(
                    "实例状态不允许记录云侧 Succeeded: " + status, HttpStatus.CONFLICT);
        }

        entity.recordCloudObservation("Succeeded", entity.getStatusReason());
        Instant now = clock.instant();
        entity.setUpdatedAt(now);
        repository.save(entity);
        lifecycleActionService.requestDelete(id, now);
    }

    /**
     * 仅落库云侧 Succeeded 证据（不做删除）。守卫：仅 CREATING/RUNNING。
     */
    public void recordCloudSucceeded(UUID id) {
        EciInstanceEntity entity = findEntity(id);
        InstanceStatus status = entity.getStatus();
        if (status != InstanceStatus.CREATING && status != InstanceStatus.RUNNING) {
            throw new BusinessException(
                    "实例状态不允许记录云侧 Succeeded: " + status, HttpStatus.CONFLICT);
        }
        entity.recordCloudObservation("Succeeded", entity.getStatusReason());
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<InstanceResponse> list(InstanceStatus status) {
        List<EciInstanceEntity> entities = status != null
                ? repository.findByStatusOrderByCreatedAtDesc(status)
                : repository.findAllByOrderByCreatedAtDesc();
        return entities.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public InstanceResponse get(UUID id) {
        return toResponse(findEntity(id));
    }

    @Transactional(readOnly = true)
    public String getRegionId(UUID id) {
        return repository.findById(id).map(EciInstanceEntity::getRegionId).orElse(null);
    }

    @Transactional
    public void recordProviderStatus(UUID id, String cloudStatus, String statusReason) {
        lifecycleActionService.recordProviderStatus(id, cloudStatus, statusReason);
    }

    /** 按云侧 Describe 结果回写本地状态（含 CREATING/FAILED 时云侧已消失 → TERMINATED）。 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public InstanceResponse refresh(UUID id) {
        EciInstanceEntity entity = findEntity(id);

        if (!StringUtils.hasText(entity.getCloudInstanceId())) {
            return toResponse(entity);
        }

        if (entity.getStatus() == InstanceStatus.TERMINATED) {
            return toResponse(entity);
        }

        String describedCloudInstanceId = entity.getCloudInstanceId();
        CloudInstanceView view = eciProvider.describe(describedCloudInstanceId, entity.getRegionId());
        return self == null
                ? applyRefresh(id, describedCloudInstanceId, view)
                : self.applyRefresh(id, describedCloudInstanceId, view);
    }

    @Transactional
    public InstanceResponse applyRefresh(UUID id, String describedCloudInstanceId, CloudInstanceView view) {
        if (self != null) {
            UUID taskId = repository.findTaskIdById(id).orElse(null);
            if (taskId != null && taskService != null) {
                taskService.lockForDeleteCompletion(taskId);
            }
        }
        EciInstanceEntity entity = self == null ? findEntity(id) : findEntityForUpdate(id);
        if (entity.getStatus() == InstanceStatus.TERMINATED
                || !Objects.equals(describedCloudInstanceId, entity.getCloudInstanceId())) {
            return toResponse(entity);
        }

        entity.recordCloudObservation(view.status(), view.statusDetail());
        entity.setUpdatedAt(Instant.now());

        InstanceStatus previous = entity.getStatus();
        if ((view.isRunning() || view.isSucceeded()) && entity.getStatus() == InstanceStatus.CREATING) {
            // Succeeded 可能跳过 Running（短任务）；本地先对齐 RUNNING，终态清理留给任务轮询器
            entity.setStatus(InstanceStatus.RUNNING);
            entity.setFailureReason(null);
        } else if (view.isFailed() && entity.getStatus() != InstanceStatus.TERMINATED
                && !entity.hasCloudSucceeded()) {
            entity.setStatus(InstanceStatus.FAILED);
            entity.setFailureReason(view.statusDetail() != null ? view.statusDetail() : view.status());
        } else if (view.isTerminatedOrMissing()
                && (entity.getStatus() == InstanceStatus.STOPPING
                || entity.getStatus() == InstanceStatus.RUNNING
                || entity.getStatus() == InstanceStatus.CREATING
                || entity.getStatus() == InstanceStatus.FAILED)) {
            // 云侧已删/不存在时对齐为 TERMINATED（含 CREATING 卡住、FAILED 后资源已清）
            entity.setStatus(InstanceStatus.TERMINATED);
            entity.setFailureReason(null);
        }

        repository.save(entity);
        if (!(view.isSucceeded() && entity.ignoresCloudSucceeded())) {
            notifyTaskOnStatusChange(entity, previous);
        }
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public boolean hasCloudSucceeded(UUID id) {
        return repository.findById(id).map(EciInstanceEntity::hasCloudSucceeded).orElse(false);
    }

    /** Short candidate scan; startup work is applied in separate per-row transactions. */
    @Transactional(readOnly = true)
    public List<UUID> findCandidateInstanceIds() {
        return repository.findByStatusIn(STARTUP_COMPENSATION_STATUSES).stream()
                .filter(entity -> switch (entity.getStatus()) {
                    case CREATING, STOPPING -> true;
                    case FAILED -> entity.getTaskId() != null && StringUtils.hasText(entity.getCloudInstanceId());
                    default -> false;
                })
                .map(EciInstanceEntity::getId)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureLegacyActionsForInstance(UUID id) {
        EciInstanceEntity entity = repository.findForUpdateById(id)
                .orElseThrow(() -> new IllegalStateException("instance not found: " + id));
        switch (entity.getStatus()) {
            case CREATING -> lifecycleActionService.ensureCreateAction(id);
            case STOPPING -> lifecycleActionService.requestDelete(id, clock.instant());
            case FAILED -> {
                if (entity.getTaskId() != null && StringUtils.hasText(entity.getCloudInstanceId())) {
                    lifecycleActionService.requestDelete(id, clock.instant());
                }
            }
            default -> {
                // A candidate may have changed state since the read-only scan.
            }
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> findLegacyCancelledTaskIds() {
        return taskRepository.findByStatusIn(LEGACY_CANCELLED_STATUS).stream()
                .map(EciTaskEntity::getId)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void migrateLegacyCancelledTask(UUID taskId) {
        EciTaskEntity task = taskRepository.findForUpdateById(taskId)
                .orElseThrow(() -> new IllegalStateException("task not found: " + taskId));
        if (task.getStatus() != TaskStatus.CANCELLED) {
            return;
        }

        UUID instanceId = task.getInstanceId();
        if (instanceId == null) {
            throw new IllegalStateException("cancelled task has no instance: " + taskId);
        }

        EciInstanceEntity instance = repository.findForUpdateById(instanceId)
                .orElseThrow(() -> new IllegalStateException("instance not found: " + instanceId));
        if (!taskId.equals(instance.getTaskId())) {
            throw new IllegalStateException("cancelled task instance association mismatch: " + taskId);
        }
        if (instance.getStatus() == InstanceStatus.TERMINATED) {
            return;
        }

        Instant now = clock.instant();
        task.setStatus(TaskStatus.CANCELLING);
        task.setEndedAt(null);
        task.setUpdatedAt(now);
        taskRepository.save(task);
        lifecycleActionService.requestDelete(instanceId, now);
    }

    private void notifyTaskOnStatusChange(EciInstanceEntity entity, InstanceStatus previous) {
        if (taskService == null || entity.getTaskId() == null || entity.getStatus() == previous) {
            return;
        }
        if (entity.getStatus() == InstanceStatus.RUNNING) {
            taskService.onInstanceRunning(entity.getTaskId());
        } else if (entity.getStatus() == InstanceStatus.FAILED) {
            taskService.onInstanceFailed(entity.getTaskId(), entity.getFailureReason());
        }
    }

    private EciInstanceEntity findEntity(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException("实例不存在: " + id, HttpStatus.NOT_FOUND));
    }

    private EciInstanceEntity findEntityForUpdate(UUID id) {
        return repository.findForUpdateById(id)
                .orElseThrow(() -> new BusinessException("实例不存在: " + id, HttpStatus.NOT_FOUND));
    }

    private String resolveShmSize(String raw, double memoryGiB) {
        return resolveShmSize(raw, memoryGiB, properties.getDefaultShmSize());
    }

    private String resolveShmSize(String raw, double memoryGiB, String defaultShmSize) {
        try {
            return ShmSize.resolve(raw, defaultShmSize, memoryGiB);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        }
    }

    private InstanceResponse toResponse(EciInstanceEntity entity) {
        return new InstanceResponse(
                entity.getId(),
                entity.getTaskId(),
                entity.getName(),
                entity.getImage(),
                entity.getCpu(),
                entity.getMemory(),
                entity.getStatus(),
                entity.getCloudInstanceId(),
                entity.getCloudStatus(),
                parseEnv(entity.getEnvJson()),
                entity.getFailureReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化 JSON 失败", e);
        }
    }

    private Map<String, String> parseEnv(String envJson) {
        if (!StringUtils.hasText(envJson)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(envJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }
}
