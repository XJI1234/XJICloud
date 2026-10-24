package com.xjicloud.eci.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.eci.common.BusinessException;
import com.xjicloud.eci.config.CloudResourceConfigService;
import com.xjicloud.eci.config.CloudResourceConfigSnapshot;
import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.instance.EciInstanceEntity;
import com.xjicloud.eci.instance.EciInstanceRepository;
import com.xjicloud.eci.instance.InstanceStatus;
import com.xjicloud.eci.instance.EciInstanceService;
import com.xjicloud.eci.instance.dto.CreateInstanceRequest;
import com.xjicloud.eci.instance.dto.InstanceResponse;
import com.xjicloud.eci.provider.CloudStatusReason;
import com.xjicloud.eci.provider.ShmSize;
import com.xjicloud.eci.task.dto.CreateTaskRequest;
import com.xjicloud.eci.task.dto.TaskResponse;
import com.xjicloud.eci.task.storage.TaskStorageResolver;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.function.Supplier;

/**
 * 任务业务服务：提交/查询/取消，并接收实例生命周期回写。
 */
@Service
public class EciTaskService {

    private static final Set<String> ALLOWED_RESTART_POLICIES = Set.of("Never", "OnFailure", "Always");

    private static final EnumSet<TaskStatus> ACTIVE_STATUSES =
            EnumSet.of(TaskStatus.PENDING, TaskStatus.STARTING, TaskStatus.RUNNING);

    private final EciTaskRepository repository;
    private final EciInstanceRepository instanceRepository;
    private final EciInstanceService instanceService;
    private final EciProperties properties;
    private final ObjectMapper objectMapper;
    private final TaskStorageResolver taskStorageResolver;
    private final TransactionTemplate createTransaction;
    private final CloudResourceConfigService cloudResourceConfigService;

    @Autowired
    public EciTaskService(
            EciTaskRepository repository,
            EciInstanceRepository instanceRepository,
            EciInstanceService instanceService,
            EciProperties properties,
            ObjectMapper objectMapper,
            TaskStorageResolver taskStorageResolver,
            PlatformTransactionManager transactionManager,
            CloudResourceConfigService cloudResourceConfigService) {
        this.repository = repository;
        this.instanceRepository = instanceRepository;
        this.instanceService = instanceService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.taskStorageResolver = taskStorageResolver;
        this.createTransaction = new TransactionTemplate(transactionManager);
        this.cloudResourceConfigService = cloudResourceConfigService;
    }

    public EciTaskService(
            EciTaskRepository repository,
            EciInstanceRepository instanceRepository,
            EciInstanceService instanceService,
            EciProperties properties,
            ObjectMapper objectMapper,
            TaskStorageResolver taskStorageResolver,
            PlatformTransactionManager transactionManager) {
        this(repository, instanceRepository, instanceService, properties, objectMapper,
                taskStorageResolver, transactionManager, null);
    }

    /**
     * 受理任务：落库实际规格 → 创建关联实例 → 状态 STARTING，立即返回。
     * <p>
     * 同一事务内完成；实例创建失败则回滚，避免留下无 instanceId 的 PENDING。
     */
    public TaskResponse create(CreateTaskRequest request) {
        String clientRequestId = resolveClientRequestId(request != null ? request.getClientRequestId() : null);
        if (clientRequestId != null) {
            EciTaskEntity existing = repository.findByClientRequestId(clientRequestId).orElse(null);
            if (existing != null) {
                return toResponse(existing);
            }
        }

        UUID taskId = UUID.randomUUID();
        Supplier<TaskSubmission> resolve = () -> resolveSubmission(request, taskId,
                cloudResourceConfigService != null ? cloudResourceConfigService.snapshot() : null);
        TaskSubmission submission = cloudResourceConfigService == null
                ? resolve.get()
                : cloudResourceConfigService.coordinate(resolve);

        try {
            return createTransaction.execute(status -> createNewTask(request, clientRequestId, submission));
        } catch (DataIntegrityViolationException exception) {
            if (clientRequestId == null) {
                throw exception;
            }
            return repository.findByClientRequestId(clientRequestId)
                    .map(this::toResponse)
                    .orElseThrow(() -> exception);
        }
    }

    private TaskSubmission resolveSubmission(
            CreateTaskRequest request, UUID taskId, CloudResourceConfigSnapshot configSnapshot) {
        if (request == null || !StringUtils.hasText(request.getImage())) {
            String defaultImage = configSnapshot != null
                    ? configSnapshot.advanced().defaultImage()
                    : properties.getDefaultImage();
            if (request == null || !StringUtils.hasText(defaultImage)) {
                throw new BusinessException("image 为必填项");
            }
        }

        String image = StringUtils.hasText(request.getImage())
                ? request.getImage().trim()
                : configSnapshot != null
                        ? configSnapshot.advanced().defaultImage().trim()
                        : properties.getDefaultImage().trim();
        double memory = request.getMemory() != null ? request.getMemory() : properties.getDefaultMemory();
        String defaultShmSize = configSnapshot != null
                ? configSnapshot.advanced().defaultShmSize()
                : properties.getDefaultShmSize();
        String shmSize = resolveShmSize(request.getShmSize(), memory, defaultShmSize);
        Map<String, String> env = request.getEnv() != null ? new HashMap<>(request.getEnv()) : new HashMap<>();
        if (request.getStorage() != null) {
            env = configSnapshot != null
                    ? taskStorageResolver.resolveAndBuildEnv(taskId, request.getStorage(), env, configSnapshot.oss())
                    : taskStorageResolver.resolveAndBuildEnv(taskId, request.getStorage(), env);
        }
        return new TaskSubmission(taskId, image, shmSize, env, configSnapshot);
    }

    private TaskResponse createNewTask(
            CreateTaskRequest request, String clientRequestId, TaskSubmission submission) {

        String restartPolicy = resolveRestartPolicy(request.getRestartPolicy());
        int maxRuntimeSec = resolveMaxRuntimeSec(request.getMaxRuntimeSec());
        double cpu = request.getCpu() != null ? request.getCpu() : properties.getDefaultCpu();
        double memory = request.getMemory() != null ? request.getMemory() : properties.getDefaultMemory();
        String shmSize = submission.shmSize();
        String image = submission.image();
        Map<String, String> env = submission.env();
        Instant now = Instant.now();
        UUID taskId = submission.taskId();
        String name = StringUtils.hasText(request.getName())
                ? request.getName().trim()
                : "task-" + taskId.toString().substring(0, 8);

        EciTaskEntity entity = new EciTaskEntity();
        entity.setId(taskId);
        entity.setClientRequestId(clientRequestId);
        entity.setName(name);
        entity.setStatus(TaskStatus.PENDING);
        entity.setImage(image);
        entity.setCommandJson(toJson(request.getCommand()));
        entity.setArgsJson(toJson(request.getArgs()));
        entity.setCpu(cpu);
        entity.setMemory(memory);
        entity.setInstanceType(StringUtils.hasText(request.getInstanceType()) ? request.getInstanceType().trim() : null);
        entity.setGpu(request.getGpu());
        entity.setImageSnapshotId(resolveImageSnapshotId(request.getImageSnapshotId()));
        entity.setShmSize(shmSize);
        entity.setRestartPolicy(restartPolicy);
        entity.setMaxRuntimeSec(maxRuntimeSec);
        // 失败取证参数在提交时快照：之后改配置不影响已提交任务的抓日志与删除宽限
        CloudResourceConfigSnapshot.Advanced advanced = submission.configSnapshot() != null
                ? submission.configSnapshot().advanced()
                : null;
        entity.setFailureLogTailLines(advanced != null
                ? advanced.failureLogTailLines()
                : properties.getFailureLogTailLines());
        entity.setFailureLogGraceSec(advanced != null
                ? advanced.failureLogGraceSec()
                : properties.getFailureLogGraceSec());
        entity.setEnvJson(toJson(env));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        if (clientRequestId != null) {
            // Claim the unique key before creating an instance; a losing concurrent request exits here.
            repository.saveAndFlush(entity);
        } else {
            repository.save(entity);
        }

        CreateInstanceRequest instanceRequest = new CreateInstanceRequest();
        instanceRequest.setName(name);
        instanceRequest.setImage(image);
        instanceRequest.setCpu(cpu);
        instanceRequest.setMemory(memory);
        instanceRequest.setEnv(env);
        instanceRequest.setInstanceType(entity.getInstanceType());
        instanceRequest.setGpu(entity.getGpu());
        instanceRequest.setImageSnapshotId(entity.getImageSnapshotId());
        instanceRequest.setShmSize(shmSize);
        instanceRequest.setRestartPolicy(restartPolicy);
        instanceRequest.setCommand(request.getCommand());
        instanceRequest.setArgs(request.getArgs());

        InstanceResponse instance = submission.configSnapshot() != null
                ? instanceService.createForTask(instanceRequest, taskId, submission.configSnapshot())
                : instanceService.createForTask(instanceRequest, taskId);

        entity.setInstanceId(instance.id());
        entity.setStatus(TaskStatus.STARTING);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);

        return toResponse(entity);
    }

    private static String resolveClientRequestId(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list(TaskStatus status) {
        List<EciTaskEntity> entities = status != null
                ? repository.findByStatusOrderByCreatedAtDesc(status)
                : repository.findAllByOrderByCreatedAtDesc();
        return entities.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse get(UUID id) {
        return toResponse(findEntity(id));
    }

    /** 取消请求先持久进入 CANCELLING，待关联 DELETE 确认后再进入 CANCELLED。 */
    @Transactional
    public TaskResponse cancel(UUID id) {
        EciTaskEntity entity = findEntity(id);

        if (entity.getStatus() == TaskStatus.CANCELLING || entity.getStatus() == TaskStatus.CANCELLED) {
            return toResponse(entity);
        }
        if (entity.getStatus() == TaskStatus.SUCCEEDED) {
            throw new BusinessException("任务已终态，无法取消: " + entity.getStatus(), HttpStatus.CONFLICT);
        }
        if (entity.getInstanceId() == null) {
            throw new BusinessException("任务未关联实例，无法取消", HttpStatus.CONFLICT);
        }
        if (entity.getStatus() == TaskStatus.FAILED) {
            instanceService.deleteForTaskCancel(entity.getInstanceId());
            return toResponse(findEntity(id));
        }

        Instant now = Instant.now();
        int updated = repository.markCancellingIfActive(
                id, TaskStatus.CANCELLING, now, ACTIVE_STATUSES);
        if (updated == 0) {
            EciTaskEntity latest = findEntity(id);
            if (latest.getStatus() == TaskStatus.CANCELLING || latest.getStatus() == TaskStatus.CANCELLED) {
                return toResponse(latest);
            }
            throw new BusinessException("任务已终态，无法取消: " + latest.getStatus(), HttpStatus.CONFLICT);
        }

        InstanceResponse instance = instanceService.deleteForTaskCancel(entity.getInstanceId());
        if (instance.status() == InstanceStatus.TERMINATED) {
            repository.markCancelledIfCancelling(
                    id, TaskStatus.CANCELLED, Instant.now(), TaskStatus.CANCELLING);
        }

        return toResponse(findEntity(id));
    }

    /**
     * 实例进入 Running 时回写任务（仅非终态 → RUNNING）。
     */
    @Transactional
    public void onInstanceRunning(UUID taskId) {
        if (taskId == null) {
            return;
        }
        repository.findById(taskId).ifPresent(entity -> {
            if (entity.getStatus() == TaskStatus.PENDING || entity.getStatus() == TaskStatus.STARTING) {
                Instant now = Instant.now();
                entity.setStatus(TaskStatus.RUNNING);
                if (entity.getStartedAt() == null) {
                    entity.setStartedAt(now);
                }
                entity.setUpdatedAt(now);
                repository.save(entity);
            }
        });
    }

    /**
     * 实例创建/运行失败时回写任务（不覆盖取消中或已终态的任务）。
     * <p>
     * 使用条件 UPDATE，避免与 cancel 的丢失更新竞态。
     */
    @Transactional
    public void onInstanceFailed(UUID taskId, String reason) {
        onInstanceFailed(taskId, reason, null);
    }

    /**
     * 同 {@link #onInstanceFailed(UUID, String)}，可附带删实例前抓取的容器日志尾部。
     */
    @Transactional
    public void onInstanceFailed(UUID taskId, String reason, String containerLogTail) {
        if (taskId == null) {
            return;
        }
        repository.markFailedIfActive(
                taskId,
                TaskStatus.FAILED,
                truncate(reason),
                blankToNull(containerLogTail),
                Instant.now(),
                ACTIVE_STATUSES);
    }

    /**
     * 云侧 Succeeded 且本地实例已 TERMINATED 后回写任务成功（不覆盖已终态）。
     */
    @Transactional
    public void markSucceeded(UUID taskId) {
        if (taskId == null) {
            return;
        }
        EciTaskEntity task = repository.findForUpdateById(taskId).orElse(null);
        if (task == null || !ACTIVE_STATUSES.contains(task.getStatus()) || task.getInstanceId() == null) {
            return;
        }
        EciInstanceEntity instance = instanceRepository.findForUpdateById(task.getInstanceId()).orElse(null);
        if (instance == null
                || !taskId.equals(instance.getTaskId())
                || instance.getStatus() != InstanceStatus.TERMINATED
                || !instance.hasCloudSucceeded()) {
            return;
        }
        repository.markSucceededIfActive(
                taskId, TaskStatus.SUCCEEDED, Instant.now(), ACTIVE_STATUSES);
    }

    /** DELETE completion shares cancel's task -> instance -> action lock order. */
    @Transactional
    public void lockForDeleteCompletion(UUID taskId) {
        if (taskId != null) {
            repository.findForUpdateById(taskId);
        }
    }

    /** DELETE completion is the only cancellation terminalization point. */
    @Transactional
    public void onInstanceTerminated(UUID taskId, String ignoredCloudStatus) {
        if (taskId == null) {
            return;
        }
        EciTaskEntity task = repository.findForUpdateById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        Instant now = Instant.now();
        if (task.getStatus() == TaskStatus.CANCELLING) {
            repository.markCancelledIfCancelling(
                    taskId, TaskStatus.CANCELLED, now, TaskStatus.CANCELLING);
        } else if (ACTIVE_STATUSES.contains(task.getStatus())) {
            markSucceeded(taskId);
        }
    }

    /** 终态轮询：STARTING / RUNNING。 */
    @Transactional(readOnly = true)
    public List<EciTaskEntity> findActiveForWatch() {
        return repository.findByStatusIn(EnumSet.of(TaskStatus.STARTING, TaskStatus.RUNNING));
    }

    /** 启动补偿：PENDING / STARTING / RUNNING。 */
    @Transactional(readOnly = true)
    public List<EciTaskEntity> findNonTerminalForCompensation() {
        return repository.findByStatusIn(
                EnumSet.of(TaskStatus.PENDING, TaskStatus.STARTING, TaskStatus.RUNNING));
    }

    private String resolveRestartPolicy(String raw) {
        if (!StringUtils.hasText(raw)) {
            return properties.getDefaultRestartPolicy();
        }
        String policy = raw.trim();
        if (!ALLOWED_RESTART_POLICIES.contains(policy)) {
            throw new BusinessException("restartPolicy 须为 Never、OnFailure 或 Always");
        }
        return policy;
    }

    private int resolveMaxRuntimeSec(Integer raw) {
        if (raw == null) {
            return properties.getDefaultMaxRuntimeSec();
        }
        if (raw <= 0) {
            throw new BusinessException("maxRuntimeSec 须为正整数");
        }
        return raw;
    }

    private static String resolveImageSnapshotId(String raw) {
        return StringUtils.hasText(raw) ? raw.trim() : null;
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

    private record TaskSubmission(
            UUID taskId,
            String image,
            String shmSize,
            Map<String, String> env,
            CloudResourceConfigSnapshot configSnapshot) {
    }

    private EciTaskEntity findEntity(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException("任务不存在: " + id, HttpStatus.NOT_FOUND));
    }

    private TaskResponse toResponse(EciTaskEntity entity) {
        EciInstanceEntity instance = entity.getInstanceId() == null
                ? null
                : instanceRepository.findById(entity.getInstanceId()).orElse(null);
        String statusReason = instance == null ? null : CloudStatusReason.safe(instance.getStatusReason());
        if (isTerminal(entity.getStatus())) {
            if (entity.getStatus() == TaskStatus.SUCCEEDED && instance != null && instance.hasCloudSucceeded()) {
                statusReason = CloudStatusReason.safe(instance.getCloudSucceededReason());
            } else if (instance != null && isTerminalCloudStatus(instance.getCloudStatus())) {
                statusReason = CloudStatusReason.safe(instance.getStatusReason());
            } else {
                statusReason = CloudStatusReason.safe(entity.getFailureReason());
            }
        }
        return new TaskResponse(
                entity.getId(),
                entity.getName(),
                entity.getStatus(),
                entity.getInstanceId(),
                instance == null ? null : instance.getStatus(),
                instance == null ? null : instance.getCloudStatus(),
                statusReason,
                instance != null && instance.getStatus() == InstanceStatus.TERMINATED,
                entity.getImage(),
                parseStringList(entity.getCommandJson()),
                parseStringList(entity.getArgsJson()),
                entity.getCpu(),
                entity.getMemory(),
                entity.getInstanceType(),
                entity.getGpu(),
                entity.getImageSnapshotId(),
                entity.getShmSize(),
                entity.getRestartPolicy(),
                entity.getMaxRuntimeSec(),
                parseEnv(entity.getEnvJson()),
                entity.getFailureReason(),
                entity.getContainerLogTail(),
                entity.getStartedAt(),
                entity.getEndedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private static boolean isTerminal(TaskStatus status) {
        return status == TaskStatus.SUCCEEDED || status == TaskStatus.FAILED || status == TaskStatus.CANCELLED;
    }

    private static boolean isTerminalCloudStatus(String cloudStatus) {
        return "Succeeded".equalsIgnoreCase(cloudStatus)
                || "Failed".equalsIgnoreCase(cloudStatus)
                || "ScheduleFailed".equalsIgnoreCase(cloudStatus)
                || "CreateFailed".equalsIgnoreCase(cloudStatus)
                || "Deleted".equalsIgnoreCase(cloudStatus)
                || "Terminated".equalsIgnoreCase(cloudStatus)
                || "NotFound".equalsIgnoreCase(cloudStatus);
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

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return "unknown error";
        }
        return reason.length() > 1000 ? reason.substring(0, 1000) : reason;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
