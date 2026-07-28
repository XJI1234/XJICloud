package com.xjicloud.eci.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.eci.common.BusinessException;
import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.instance.EciInstanceService;
import com.xjicloud.eci.instance.dto.CreateInstanceRequest;
import com.xjicloud.eci.instance.dto.InstanceResponse;
import com.xjicloud.eci.task.dto.CreateTaskRequest;
import com.xjicloud.eci.task.dto.TaskResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
 * 任务业务服务：提交/查询/取消，并接收实例生命周期回写。
 */
@Service
public class EciTaskService {

    private static final Set<String> ALLOWED_RESTART_POLICIES = Set.of("Never", "OnFailure", "Always");

    private static final EnumSet<TaskStatus> ACTIVE_STATUSES =
            EnumSet.of(TaskStatus.PENDING, TaskStatus.STARTING, TaskStatus.RUNNING);

    private final EciTaskRepository repository;
    private final EciInstanceService instanceService;
    private final EciProperties properties;
    private final ObjectMapper objectMapper;

    public EciTaskService(
            EciTaskRepository repository,
            EciInstanceService instanceService,
            EciProperties properties,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.instanceService = instanceService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 受理任务：落库实际规格 → 创建关联实例 → 状态 STARTING，立即返回。
     * <p>
     * 同一事务内完成；实例创建失败则回滚，避免留下无 instanceId 的 PENDING。
     */
    @Transactional
    public TaskResponse create(CreateTaskRequest request) {
        if (request == null || !StringUtils.hasText(request.getImage())) {
            throw new BusinessException("image 为必填项");
        }

        String restartPolicy = resolveRestartPolicy(request.getRestartPolicy());
        int maxRuntimeSec = resolveMaxRuntimeSec(request.getMaxRuntimeSec());
        double cpu = request.getCpu() != null ? request.getCpu() : properties.getDefaultCpu();
        double memory = request.getMemory() != null ? request.getMemory() : properties.getDefaultMemory();
        String image = request.getImage().trim();
        Map<String, String> env = request.getEnv() != null ? new HashMap<>(request.getEnv()) : new HashMap<>();
        Instant now = Instant.now();
        UUID taskId = UUID.randomUUID();
        String name = StringUtils.hasText(request.getName())
                ? request.getName().trim()
                : "task-" + taskId.toString().substring(0, 8);

        EciTaskEntity entity = new EciTaskEntity();
        entity.setId(taskId);
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
        entity.setRestartPolicy(restartPolicy);
        entity.setMaxRuntimeSec(maxRuntimeSec);
        entity.setEnvJson(toJson(env));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        repository.save(entity);

        CreateInstanceRequest instanceRequest = new CreateInstanceRequest();
        instanceRequest.setName(name);
        instanceRequest.setImage(image);
        instanceRequest.setCpu(cpu);
        instanceRequest.setMemory(memory);
        instanceRequest.setEnv(env);
        instanceRequest.setInstanceType(entity.getInstanceType());
        instanceRequest.setGpu(entity.getGpu());
        instanceRequest.setImageSnapshotId(entity.getImageSnapshotId());
        instanceRequest.setRestartPolicy(restartPolicy);
        instanceRequest.setCommand(request.getCommand());
        instanceRequest.setArgs(request.getArgs());

        InstanceResponse instance = instanceService.createForTask(instanceRequest, taskId);

        entity.setInstanceId(instance.id());
        entity.setStatus(TaskStatus.STARTING);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);

        return toResponse(entity);
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

    /**
     * 取消任务：幂等 CANCELLED；SUCCEEDED/FAILED 拒绝；否则删关联实例。
     * <p>
     * 条件更新避免与超时 FAILED 竞态互相覆盖；删除失败则回滚。
     */
    @Transactional
    public TaskResponse cancel(UUID id) {
        EciTaskEntity entity = findEntity(id);

        if (entity.getStatus() == TaskStatus.CANCELLED) {
            return toResponse(entity);
        }
        if (entity.getStatus() == TaskStatus.SUCCEEDED || entity.getStatus() == TaskStatus.FAILED) {
            throw new BusinessException("任务已终态，无法取消: " + entity.getStatus(), HttpStatus.CONFLICT);
        }

        Instant now = Instant.now();
        int updated = repository.markCancelledIfActive(
                id, TaskStatus.CANCELLED, now, ACTIVE_STATUSES);
        if (updated == 0) {
            EciTaskEntity latest = findEntity(id);
            if (latest.getStatus() == TaskStatus.CANCELLED) {
                return toResponse(latest);
            }
            throw new BusinessException("任务已终态，无法取消: " + latest.getStatus(), HttpStatus.CONFLICT);
        }

        if (entity.getInstanceId() != null) {
            instanceService.deleteForTaskCancel(entity.getInstanceId());
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
     * 实例创建/运行失败时回写任务（不覆盖已终态，含 CANCELLED）。
     * <p>
     * 使用条件 UPDATE，避免与 cancel 的丢失更新竞态。
     */
    @Transactional
    public void onInstanceFailed(UUID taskId, String reason) {
        if (taskId == null) {
            return;
        }
        repository.markFailedIfActive(
                taskId, TaskStatus.FAILED, truncate(reason), Instant.now(), ACTIVE_STATUSES);
    }

    /**
     * 云侧 Succeeded 且本地实例已 TERMINATED 后回写任务成功（不覆盖已终态）。
     */
    @Transactional
    public void markSucceeded(UUID taskId) {
        if (taskId == null) {
            return;
        }
        repository.markSucceededIfActive(
                taskId, TaskStatus.SUCCEEDED, Instant.now(), ACTIVE_STATUSES);
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

    private EciTaskEntity findEntity(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException("任务不存在: " + id, HttpStatus.NOT_FOUND));
    }

    private TaskResponse toResponse(EciTaskEntity entity) {
        return new TaskResponse(
                entity.getId(),
                entity.getName(),
                entity.getStatus(),
                entity.getInstanceId(),
                entity.getImage(),
                parseStringList(entity.getCommandJson()),
                parseStringList(entity.getArgsJson()),
                entity.getCpu(),
                entity.getMemory(),
                entity.getInstanceType(),
                entity.getGpu(),
                entity.getImageSnapshotId(),
                entity.getRestartPolicy(),
                entity.getMaxRuntimeSec(),
                parseEnv(entity.getEnvJson()),
                entity.getFailureReason(),
                entity.getStartedAt(),
                entity.getEndedAt(),
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
}
