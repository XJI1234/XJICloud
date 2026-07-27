package com.xjicloud.eci.instance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.eci.common.BusinessException;
import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.instance.dto.CreateInstanceRequest;
import com.xjicloud.eci.instance.dto.InstanceResponse;
import com.xjicloud.eci.lifecycle.AsyncLifecycleWorker;
import com.xjicloud.eci.provider.CloudInstanceView;
import com.xjicloud.eci.provider.CreateSpec;
import com.xjicloud.eci.provider.EciProvider;
import com.xjicloud.eci.task.EciTaskService;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 实例业务服务：维护本地状态机权威，编排异步创建/删除，并支持按云状态 refresh。
 */
@Service
public class EciInstanceService {

    private static final String INTERRUPTED_CREATE_REASON = "进程中断，创建未提交到云";

    private static final Set<InstanceStatus> COMPENSATION_STATUSES = EnumSet.of(
            InstanceStatus.CREATING,
            InstanceStatus.STOPPING,
            InstanceStatus.RUNNING);

    private final EciInstanceRepository repository;
    private final EciProperties properties;
    private final EciProvider eciProvider;
    private final AsyncLifecycleWorker lifecycleWorker;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    /** 任务状态回写；单测可传 null。 */
    private final EciTaskService taskService;
    /** 经 Spring 代理调用，使 {@link #refresh} 的 {@code @Transactional} 在补偿路径生效。 */
    private final EciInstanceService self;

    public EciInstanceService(
            EciInstanceRepository repository,
            EciProperties properties,
            EciProvider eciProvider,
            AsyncLifecycleWorker lifecycleWorker,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Lazy EciTaskService taskService,
            @Lazy EciInstanceService self) {
        this.repository = repository;
        this.properties = properties;
        this.eciProvider = eciProvider;
        this.lifecycleWorker = lifecycleWorker;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.taskService = taskService;
        this.self = self;
    }

    /**
     * 受理创建：落库 CREATING 后立即返回，后台由 {@link AsyncLifecycleWorker} 调云并轮询。
     * <p>
     * 先 {@code save} 再调度异步，避免事务未提交时异步线程读不到记录。
     */
    public InstanceResponse create(CreateInstanceRequest request) {
        return createInternal(request, null);
    }

    /** 任务驱动创建：绑定 {@code taskId}。 */
    public InstanceResponse createForTask(CreateInstanceRequest request, UUID taskId) {
        if (taskId == null) {
            throw new BusinessException("taskId 不能为空");
        }
        return createInternal(request, taskId);
    }

    private InstanceResponse createInternal(CreateInstanceRequest request, UUID taskId) {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();

        String name = StringUtils.hasText(request.getName()) ? request.getName() : "demo-" + id.toString().substring(0, 8);
        String image = StringUtils.hasText(request.getImage()) ? request.getImage() : properties.getDefaultImage();
        double cpu = request.getCpu() != null ? request.getCpu() : properties.getDefaultCpu();
        double memory = request.getMemory() != null ? request.getMemory() : properties.getDefaultMemory();
        Map<String, String> env = request.getEnv() != null ? new HashMap<>(request.getEnv()) : new HashMap<>();
        String instanceType = StringUtils.hasText(request.getInstanceType()) ? request.getInstanceType() : null;
        Integer gpu = request.getGpu();
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
        entity.setRestartPolicy(restartPolicy);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        // 单独提交后再触发异步，避免 CREATING 记录尚未可见
        repository.save(entity);

        CreateSpec spec = new CreateSpec(
                name,
                image,
                cpu,
                memory,
                env,
                instanceType,
                gpu,
                restartPolicy,
                command,
                args);
        lifecycleWorker.scheduleCreate(id, spec);

        return toResponse(entity);
    }

    /**
     * 受理删除：TERMINATED/STOPPING 幂等；CREATING 中拒绝；否则置 STOPPING 并异步删云。
     */
    public InstanceResponse delete(UUID id) {
        EciInstanceEntity entity = findEntity(id);

        if (entity.getStatus() == InstanceStatus.TERMINATED) {
            return toResponse(entity);
        }

        if (entity.getStatus() == InstanceStatus.STOPPING) {
            return toResponse(entity);
        }

        if (entity.getStatus() == InstanceStatus.CREATING) {
            throw new BusinessException("实例仍在创建中，请稍后再删除", HttpStatus.CONFLICT);
        }

        entity.setStatus(InstanceStatus.STOPPING);
        entity.setFailureReason(null);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);

        lifecycleWorker.scheduleDelete(id);
        return toResponse(entity);
    }

    /**
     * 任务取消专用删除：允许 CREATING；无 cloudId 时本地 TERMINATED，有则 STOPPING + 异步删。
     */
    public InstanceResponse deleteForTaskCancel(UUID id) {
        EciInstanceEntity entity = findEntity(id);

        if (entity.getStatus() == InstanceStatus.TERMINATED
                || entity.getStatus() == InstanceStatus.STOPPING) {
            return toResponse(entity);
        }

        if (entity.getStatus() == InstanceStatus.CREATING
                && !StringUtils.hasText(entity.getCloudInstanceId())) {
            entity.setStatus(InstanceStatus.TERMINATED);
            entity.setCloudStatus("Deleted");
            entity.setFailureReason(null);
            entity.setUpdatedAt(Instant.now());
            repository.save(entity);
            return toResponse(entity);
        }

        entity.setStatus(InstanceStatus.STOPPING);
        entity.setFailureReason(null);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);

        lifecycleWorker.scheduleDelete(id);
        return toResponse(entity);
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

    /** 按云侧 Describe 结果回写本地状态（含 CREATING/FAILED 时云侧已消失 → TERMINATED）。 */
    @Transactional
    public InstanceResponse refresh(UUID id) {
        EciInstanceEntity entity = findEntity(id);

        if (!StringUtils.hasText(entity.getCloudInstanceId())) {
            return toResponse(entity);
        }

        if (entity.getStatus() == InstanceStatus.TERMINATED) {
            return toResponse(entity);
        }

        CloudInstanceView view = eciProvider.describe(entity.getCloudInstanceId());
        entity.setCloudStatus(view.status());
        entity.setUpdatedAt(Instant.now());

        InstanceStatus previous = entity.getStatus();
        if ((view.isRunning() || view.isSucceeded()) && entity.getStatus() == InstanceStatus.CREATING) {
            // Succeeded 可能跳过 Running（短任务）；本地先对齐 RUNNING，终态清理留给任务轮询器
            entity.setStatus(InstanceStatus.RUNNING);
            entity.setFailureReason(null);
        } else if (view.isFailed() && entity.getStatus() != InstanceStatus.TERMINATED) {
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
        notifyTaskOnStatusChange(entity, previous);
        return toResponse(entity);
    }

    /**
     * 启动补偿扫描范围：CREATING / STOPPING / RUNNING（只读，短查询）。
     * <p>
     * 本方法不加写事务；逐条补偿见 {@link #compensateOne(UUID)}。
     */
    @Transactional(readOnly = true)
    public List<UUID> listIdsForStartupCompensation() {
        return repository.findByStatusIn(COMPENSATION_STATUSES).stream()
                .map(EciInstanceEntity::getId)
                .toList();
    }

    /**
     * 对单条未终态实例做启动补偿。方法本身<strong>不加</strong>长事务；
     * 写库经 {@link TransactionTemplate} 或代理调用的 {@link #refresh} 按条提交。
     */
    public CompensationOutcome compensateOne(UUID id) {
        EciInstanceEntity entity = repository.findById(id).orElse(null);
        if (entity == null || !COMPENSATION_STATUSES.contains(entity.getStatus())) {
            return CompensationOutcome.SKIPPED;
        }

        return switch (entity.getStatus()) {
            case CREATING -> compensateCreating(entity);
            case STOPPING -> compensateStopping(entity);
            case RUNNING -> {
                if (StringUtils.hasText(entity.getCloudInstanceId())) {
                    self.refresh(id);
                }
                yield CompensationOutcome.REFRESHED_ONLY;
            }
            default -> CompensationOutcome.SKIPPED;
        };
    }

    private CompensationOutcome compensateCreating(EciInstanceEntity entity) {
        UUID id = entity.getId();
        if (!StringUtils.hasText(entity.getCloudInstanceId())) {
            markInterruptedCreating(id);
            return CompensationOutcome.FAILED_LOCAL;
        }
        InstanceResponse refreshed = self.refresh(id);
        if (refreshed.status() == InstanceStatus.CREATING) {
            lifecycleWorker.resumeCreatePoll(id, refreshed.cloudInstanceId());
            return CompensationOutcome.RESUMED_CREATE;
        }
        return CompensationOutcome.REFRESHED_ONLY;
    }

    private CompensationOutcome compensateStopping(EciInstanceEntity entity) {
        UUID id = entity.getId();
        if (!StringUtils.hasText(entity.getCloudInstanceId())) {
            markTerminatedLocal(id);
            return CompensationOutcome.TERMINATED_LOCAL;
        }
        InstanceResponse refreshed = self.refresh(id);
        if (refreshed.status() == InstanceStatus.STOPPING) {
            lifecycleWorker.resumeDelete(id);
            return CompensationOutcome.RESUMED_DELETE;
        }
        return CompensationOutcome.REFRESHED_ONLY;
    }

    private void markInterruptedCreating(UUID id) {
        final UUID[] taskIdHolder = new UUID[1];
        transactionTemplate.executeWithoutResult(status -> repository.findById(id).ifPresent(entity -> {
            if (entity.getStatus() != InstanceStatus.CREATING) {
                return;
            }
            entity.setStatus(InstanceStatus.FAILED);
            entity.setFailureReason(INTERRUPTED_CREATE_REASON);
            entity.setUpdatedAt(Instant.now());
            repository.save(entity);
            taskIdHolder[0] = entity.getTaskId();
        }));
        if (taskIdHolder[0] != null && taskService != null) {
            taskService.onInstanceFailed(taskIdHolder[0], INTERRUPTED_CREATE_REASON);
        }
    }

    private void markTerminatedLocal(UUID id) {
        transactionTemplate.executeWithoutResult(status -> repository.findById(id).ifPresent(entity -> {
            entity.setStatus(InstanceStatus.TERMINATED);
            entity.setCloudStatus("Deleted");
            entity.setFailureReason(null);
            entity.setUpdatedAt(Instant.now());
            repository.save(entity);
        }));
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
