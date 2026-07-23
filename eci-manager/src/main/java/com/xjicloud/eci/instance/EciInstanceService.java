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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EciInstanceService {

    private final EciInstanceRepository repository;
    private final EciProperties properties;
    private final EciProvider eciProvider;
    private final AsyncLifecycleWorker lifecycleWorker;
    private final ObjectMapper objectMapper;

    public EciInstanceService(
            EciInstanceRepository repository,
            EciProperties properties,
            EciProvider eciProvider,
            AsyncLifecycleWorker lifecycleWorker,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.properties = properties;
        this.eciProvider = eciProvider;
        this.lifecycleWorker = lifecycleWorker;
        this.objectMapper = objectMapper;
    }

    public InstanceResponse create(CreateInstanceRequest request) {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();

        String name = StringUtils.hasText(request.getName()) ? request.getName() : "demo-" + id.toString().substring(0, 8);
        String image = StringUtils.hasText(request.getImage()) ? request.getImage() : properties.getDefaultImage();
        double cpu = request.getCpu() != null ? request.getCpu() : properties.getDefaultCpu();
        double memory = request.getMemory() != null ? request.getMemory() : properties.getDefaultMemory();
        Map<String, String> env = request.getEnv() != null ? new HashMap<>(request.getEnv()) : new HashMap<>();

        EciInstanceEntity entity = new EciInstanceEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setImage(image);
        entity.setCpu(cpu);
        entity.setMemory(memory);
        entity.setStatus(InstanceStatus.CREATING);
        entity.setEnvJson(toJson(env));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        // save commits in its own transaction before async work starts
        repository.save(entity);

        CreateSpec spec = new CreateSpec(name, image, cpu, memory, env);
        lifecycleWorker.scheduleCreate(id, spec);

        return toResponse(entity);
    }

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

        if (view.isRunning() && entity.getStatus() == InstanceStatus.CREATING) {
            entity.setStatus(InstanceStatus.RUNNING);
            entity.setFailureReason(null);
        } else if (view.isFailed() && entity.getStatus() != InstanceStatus.TERMINATED) {
            entity.setStatus(InstanceStatus.FAILED);
            entity.setFailureReason(view.statusDetail() != null ? view.statusDetail() : view.status());
        } else if (view.isTerminatedOrMissing()
                && (entity.getStatus() == InstanceStatus.STOPPING
                || entity.getStatus() == InstanceStatus.RUNNING
                || entity.getStatus() == InstanceStatus.CREATING)) {
            // CREATING + cloud gone (deleted externally / never found) must leave CREATING
            entity.setStatus(InstanceStatus.TERMINATED);
            entity.setFailureReason(null);
        }

        repository.save(entity);
        return toResponse(entity);
    }

    private EciInstanceEntity findEntity(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException("实例不存在: " + id, HttpStatus.NOT_FOUND));
    }

    private InstanceResponse toResponse(EciInstanceEntity entity) {
        return new InstanceResponse(
                entity.getId(),
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

    private String toJson(Map<String, String> env) {
        try {
            return objectMapper.writeValueAsString(env);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化 env 失败", e);
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
