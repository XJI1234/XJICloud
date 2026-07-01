package com.xjicloud.framework.eci;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.eci.model.v20180808.CreateContainerGroupRequest;
import com.aliyuncs.eci.model.v20180808.CreateContainerGroupResponse;
import com.aliyuncs.eci.model.v20180808.DeleteContainerGroupRequest;
import com.aliyuncs.eci.model.v20180808.DescribeContainerGroupsRequest;
import com.aliyuncs.eci.model.v20180808.DescribeContainerGroupsResponse;
import com.aliyuncs.profile.DefaultProfile;
import com.xjicloud.framework.common.BusinessException;
import com.xjicloud.framework.config.FrameworkProperties;
import com.xjicloud.framework.configregistry.RuntimeConfigDocument.AliyunConfig;
import com.xjicloud.framework.configregistry.RuntimeConfigDocument.BackendConfig;
import com.xjicloud.framework.configregistry.RuntimeConfigDocument.WorkerConfig;
import com.xjicloud.framework.configregistry.RuntimeConfigService;
import com.xjicloud.framework.configregistry.SecretCrypto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EciInstanceService {

    private static final Logger log = LoggerFactory.getLogger(EciInstanceService.class);

    private final EciInstanceRepository repository;
    private final RuntimeConfigService configService;
    private final SecretCrypto crypto;
    private final FrameworkProperties properties;

    public EciInstanceService(
            EciInstanceRepository repository,
            RuntimeConfigService configService,
            SecretCrypto crypto,
            FrameworkProperties properties
    ) {
        this.repository = repository;
        this.configService = configService;
        this.crypto = crypto;
        this.properties = properties;
    }

    public List<EciInstanceView> listInstances() {
        refreshRemoteStatuses();
        return repository.findAllByOrderByCreatedAtDesc().stream().map(EciInstanceView::from).toList();
    }

    @Transactional
    public EciInstanceView createInstance(String name) {
        AliyunConfig aliyun = configService.getSnapshot().config().aliyun();
        WorkerConfig worker = configService.getSnapshot().config().worker();
        BackendConfig backend = configService.getSnapshot().config().backend();
        if (aliyun == null || aliyun.vSwitchId() == null || aliyun.securityGroupId() == null) {
            throw new BusinessException("请先在配置中心完善阿里云 VPC/交换机/安全组");
        }
        String ak = resolveAk(aliyun);
        String sk = resolveSk(aliyun);
        if (ak == null || sk == null) {
            throw new BusinessException("请配置阿里云 AccessKey");
        }
        String region = aliyun.regionId() != null ? aliyun.regionId() : "cn-hangzhou";
        String image = aliyun.containerImage() != null ? aliyun.containerImage()
                : properties.aliyun().containerImage();
        String backendUrl = backend != null && backend.publicUrl() != null
                ? backend.publicUrl() : properties.backendUrl();
        String workerSecret = worker != null ? crypto.decrypt(worker.sharedSecret()) : "change-me";
        String instanceName = name != null && !name.isBlank() ? name : "xjicloud-worker-" + System.currentTimeMillis();

        try {
            IAcsClient client = buildClient(region, ak, sk);
            CreateContainerGroupRequest request = new CreateContainerGroupRequest();
            request.setRegionId(region);
            request.setSecurityGroupId(aliyun.securityGroupId());
            request.setVSwitchId(aliyun.vSwitchId());
            request.setContainerGroupName(instanceName);

            List<CreateContainerGroupRequest.Container> containers = new ArrayList<>();
            CreateContainerGroupRequest.Container container = new CreateContainerGroupRequest.Container();
            container.setName("gpu-worker");
            container.setImage(image);
            container.setEnvironmentVars(buildEnv(backendUrl, workerSecret, instanceName));
            containers.add(container);
            request.setContainers(containers);

            CreateContainerGroupResponse response = client.getAcsResponse(request);
            EciInstance entity = new EciInstance();
            entity.setContainerGroupId(response.getContainerGroupId());
            entity.setName(instanceName);
            entity.setLinkedWorkerName(instanceName);
            entity.setStatus(EciStatus.PENDING);
            repository.save(entity);
            return EciInstanceView.from(entity);
        } catch (Exception e) {
            log.error("ECI create failed", e);
            throw new BusinessException("创建 ECI 失败: " + e.getMessage());
        }
    }

    @Transactional
    public void stopInstance(java.util.UUID id) {
        EciInstance entity = repository.findById(id).orElseThrow(() -> new BusinessException("实例不存在"));
        AliyunConfig aliyun = configService.getSnapshot().config().aliyun();
        try {
            IAcsClient client = buildClient(
                    aliyun.regionId(), resolveAk(aliyun), resolveSk(aliyun));
            DeleteContainerGroupRequest request = new DeleteContainerGroupRequest();
            request.setContainerGroupId(entity.getContainerGroupId());
            request.setRegionId(aliyun.regionId());
            client.getAcsResponse(request);
            entity.setStatus(EciStatus.STOPPED);
            entity.setUpdatedAt(Instant.now());
            repository.save(entity);
        } catch (Exception e) {
            throw new BusinessException("停止 ECI 失败: " + e.getMessage());
        }
    }

    public boolean hasAvailableCapacity() {
        refreshRemoteStatuses();
        return repository.findByStatusIn(List.of(EciStatus.RUNNING, EciStatus.PENDING)).stream()
                .findAny()
                .isPresent();
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void refreshRemoteStatuses() {
        AliyunConfig aliyun = configService.getSnapshot().config().aliyun();
        if (aliyun == null || aliyun.accessKeyId() == null) {
            return;
        }
        String ak = resolveAk(aliyun);
        String sk = resolveSk(aliyun);
        if (ak == null || sk == null) {
            return;
        }
        try {
            IAcsClient client = buildClient(aliyun.regionId(), ak, sk);
            for (EciInstance instance : repository.findAll()) {
                if (instance.getContainerGroupId() == null) continue;
                DescribeContainerGroupsRequest req = new DescribeContainerGroupsRequest();
                req.setRegionId(aliyun.regionId());
                req.setContainerGroupIds("[\"" + instance.getContainerGroupId() + "\"]");
                DescribeContainerGroupsResponse resp = client.getAcsResponse(req);
                if (resp.getContainerGroups() != null && !resp.getContainerGroups().isEmpty()) {
                    instance.setStatus(mapStatus(resp.getContainerGroups().get(0).getStatus()));
                }
                instance.setUpdatedAt(Instant.now());
                repository.save(instance);
            }
        } catch (Exception e) {
            log.debug("ECI status refresh failed: {}", e.getMessage());
        }
    }

    private List<CreateContainerGroupRequest.Container.EnvironmentVar> buildEnv(
            String backendUrl, String workerSecret, String workerName
    ) {
        List<CreateContainerGroupRequest.Container.EnvironmentVar> envs = new ArrayList<>();
        envs.add(env("XJICLOUD_BACKEND_URL", backendUrl));
        envs.add(env("WORKER_SECRET", workerSecret));
        envs.add(env("WORKER_NAME", workerName));
        return envs;
    }

    private CreateContainerGroupRequest.Container.EnvironmentVar env(String key, String value) {
        CreateContainerGroupRequest.Container.EnvironmentVar ev = new CreateContainerGroupRequest.Container.EnvironmentVar();
        ev.setKey(key);
        ev.setValue(value);
        return ev;
    }

    private IAcsClient buildClient(String region, String ak, String sk) {
        DefaultProfile profile = DefaultProfile.getProfile(region, ak, sk);
        return new DefaultAcsClient(profile);
    }

    private String resolveAk(AliyunConfig aliyun) {
        if (aliyun.accessKeyId() != null && !aliyun.accessKeyId().contains("***")) {
            return aliyun.accessKeyId();
        }
        return properties.aliyun() != null ? properties.aliyun().accessKeyId() : null;
    }

    private String resolveSk(AliyunConfig aliyun) {
        if (aliyun.accessKeySecret() != null && !aliyun.accessKeySecret().contains("***")) {
            return crypto.decrypt(aliyun.accessKeySecret());
        }
        return properties.aliyun() != null ? properties.aliyun().accessKeySecret() : null;
    }

    private EciStatus mapStatus(String status) {
        if (status == null) return EciStatus.PENDING;
        return switch (status.toLowerCase()) {
            case "running" -> EciStatus.RUNNING;
            case "succeeded" -> EciStatus.SUCCEEDED;
            case "failed" -> EciStatus.FAILED;
            case "scheduling", "pending" -> EciStatus.PENDING;
            default -> EciStatus.PENDING;
        };
    }
}
