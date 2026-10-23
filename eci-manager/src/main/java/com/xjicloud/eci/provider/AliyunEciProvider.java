package com.xjicloud.eci.provider;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.auth.AlibabaCloudCredentialsProvider;
import com.aliyuncs.auth.DefaultCredentialsProvider;
import com.aliyuncs.auth.InstanceProfileCredentialsProvider;
import com.aliyuncs.eci.model.v20180808.CreateContainerGroupRequest;
import com.aliyuncs.eci.model.v20180808.CreateContainerGroupResponse;
import com.aliyuncs.eci.model.v20180808.DeleteContainerGroupRequest;
import com.aliyuncs.eci.model.v20180808.DescribeContainerGroupsRequest;
import com.aliyuncs.eci.model.v20180808.DescribeContainerGroupsResponse;
import com.aliyuncs.eci.model.v20180808.DescribeContainerLogRequest;
import com.aliyuncs.eci.model.v20180808.DescribeContainerLogResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.credential.AccessKeyPair;
import com.xjicloud.eci.credential.AliyunCredentialMode;
import com.xjicloud.eci.credential.AliyunCredentialResolver;
import com.xjicloud.eci.credential.CredentialHint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 阿里云 ECI OpenAPI 实现：CreateContainerGroup / DescribeContainerGroups / DeleteContainerGroup。
 * <p>
 * 成对运行时 AccessKey 时走 STATIC_AK；否则 ECS RAM 角色或 SDK 默认链在首次请求时解析。
 * 日志只记录实例 ID、credentialMode 与错误码，不打印凭证 / Api-Key。
 */
public class AliyunEciProvider implements EciProvider {

    private static final Logger log = LoggerFactory.getLogger(AliyunEciProvider.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String CREDENTIAL_HINT =
            "无法解析阿里云凭证：生产请为 ECS 绑定 RAM 角色（可配 eci.ecs-ram-role-name）；"
                    + "本机请用 ECI_ACCESS_KEY_ID/SECRET 作为种子，或 PUT /api/v1/eci/runtime-credentials 写入当前钥。"
                    + "非 ECS 上出现连接 100.100.100.200 超时属预期。";

    private static final Set<String> REJECTED_CREATE_ERROR_CODES = Set.of(
            "InvalidParameter", "InvalidParameterValue", "Forbidden", "Forbidden.RAM",
            "Unauthorized", "AccessDenied", "InvalidAccessKeyId", "SignatureDoesNotMatch",
            "QuotaExceeded", "OperationDenied.QuotaExceeded");

    private final EciProperties properties;
    private volatile IAcsClient client;

    public AliyunEciProvider(EciProperties properties) {
        this(properties, new AccessKeyPair(properties.getAccessKeyId(), properties.getAccessKeySecret()));
    }

    public AliyunEciProvider(EciProperties properties, AccessKeyPair runtimeKeys) {
        if (properties.isMock()) {
            throw new IllegalStateException("AliyunEciProvider must not be constructed when eci.mock=true");
        }
        this.properties = properties;
        String regionId = properties.getRegionId();
        String roleName = properties.getEcsRamRoleName();
        String accessKeyId = runtimeKeys == null ? null : runtimeKeys.accessKeyId();
        String accessKeySecret = runtimeKeys == null ? null : runtimeKeys.accessKeySecret();
        AliyunCredentialMode mode = AliyunCredentialResolver.resolve(accessKeyId, accessKeySecret, roleName);
        if (mode == AliyunCredentialMode.STATIC_AK) {
            this.client = staticClient(accessKeyId, accessKeySecret);
            log.info("ECI Aliyun client ready credentialMode={} region={}", mode, regionId);
            return;
        }
        String credentialMode;
        AlibabaCloudCredentialsProvider creds;
        try {
            if (StringUtils.hasText(roleName)) {
                credentialMode = "INSTANCE_PROFILE";
                creds = new InstanceProfileCredentialsProvider(roleName.trim());
            } else {
                credentialMode = "DEFAULT_CHAIN";
                creds = new DefaultCredentialsProvider();
            }
        } catch (ClientException e) {
            throw new IllegalStateException(CREDENTIAL_HINT + " 原因: " + e.getErrMsg(), e);
        } catch (RuntimeException e) {
            throw new IllegalStateException(CREDENTIAL_HINT + " 原因: " + e.getMessage(), e);
        }
        DefaultProfile profile = DefaultProfile.getProfile(regionId);
        this.client = new DefaultAcsClient(profile, creds);
        log.info("ECI Aliyun client ready credentialMode={} region={}", credentialMode, regionId);
    }

    AliyunEciProvider(EciProperties properties, IAcsClient client) {
        if (properties.isMock()) {
            throw new IllegalStateException("AliyunEciProvider must not be constructed when eci.mock=true");
        }
        this.properties = properties;
        this.client = client;
    }

    @Override
    public void reloadAccessKey(String accessKeyId, String accessKeySecret) {
        this.client = staticClient(accessKeyId, accessKeySecret);
        log.info("ECI Aliyun client reloaded credentialMode={} akHint={} region={}",
                AliyunCredentialMode.STATIC_AK,
                CredentialHint.mask(accessKeyId),
                properties.getRegionId());
    }

    private IAcsClient staticClient(String accessKeyId, String accessKeySecret) {
        if (!new AccessKeyPair(accessKeyId, accessKeySecret).isComplete()) {
            throw new IllegalArgumentException("accessKeyId 与 accessKeySecret 必须成对非空");
        }
        DefaultProfile profile = DefaultProfile.getProfile(
                properties.getRegionId(), accessKeyId.trim(), accessKeySecret.trim());
        return new DefaultAcsClient(profile);
    }

    @Override
    public String create(CreateSpec spec, String clientToken) {
        if (!StringUtils.hasText(clientToken)) {
            throw new IllegalArgumentException("clientToken must not be blank");
        }
        CreateContainerGroupRequest request = new CreateContainerGroupRequest();
        request.setRegionId(properties.getRegionId());
        request.setSecurityGroupId(properties.getSecurityGroupId());
        request.setVSwitchId(properties.getVswitchId());
        request.setClientToken(clientToken);
        CreateContainerGroupRequest.Tag tag = new CreateContainerGroupRequest.Tag();
        tag.setKey("xjicloud-local-instance-id");
        tag.setValue(clientToken);
        request.setTags(List.of(tag));
        if (StringUtils.hasText(spec.instanceType())) {
            request.setInstanceType(spec.instanceType().trim());
        }
        if (StringUtils.hasText(spec.restartPolicy())) {
            request.setRestartPolicy(spec.restartPolicy().trim());
        }
        if (StringUtils.hasText(spec.imageSnapshotId())) {
            request.setImageSnapshotId(spec.imageSnapshotId().trim());
        }
        // 容器组名与容器名共用同一规范化名称，避免两次随机后缀不一致
        String resourceName = sanitizeName(spec.name(), clientToken);
        request.setContainerGroupName(resourceName);

        CreateContainerGroupRequest.Container container = new CreateContainerGroupRequest.Container();
        container.setName(resourceName);
        container.setImage(spec.image());
        container.setCpu((float) spec.cpu());
        container.setMemory((float) spec.memory());
        if (spec.gpu() != null && spec.gpu() > 0) {
            container.setGpu(spec.gpu());
        }
        if (spec.command() != null && !spec.command().isEmpty()) {
            container.setCommands(spec.command());
        }
        if (spec.args() != null && !spec.args().isEmpty()) {
            container.setArgs(spec.args());
        }

        if (spec.env() != null && !spec.env().isEmpty()) {
            List<CreateContainerGroupRequest.Container.EnvironmentVar> envVars = new ArrayList<>();
            for (Map.Entry<String, String> entry : spec.env().entrySet()) {
                CreateContainerGroupRequest.Container.EnvironmentVar envVar =
                        new CreateContainerGroupRequest.Container.EnvironmentVar();
                envVar.setKey(entry.getKey());
                envVar.setValue(entry.getValue());
                envVars.add(envVar);
            }
            container.setEnvironmentVars(envVars);
        }

        attachShmVolume(request, container, spec.shmSize());

        request.setContainers(List.of(container));

        try {
            long start = System.currentTimeMillis();
            CreateContainerGroupResponse response = client.getAcsResponse(request);
            log.info("ECI create ok cloudInstanceId={} instanceType={} gpu={} restartPolicy={} imageSnapshotId={} shmSize={} elapsedMs={}",
                    response.getContainerGroupId(),
                    spec.instanceType(),
                    spec.gpu(),
                    spec.restartPolicy(),
                    spec.imageSnapshotId(),
                    spec.shmSize(),
                    System.currentTimeMillis() - start);
            return response.getContainerGroupId();
        } catch (ClientException e) {
            log.error("ECI create failed errCode={} requestId={}", e.getErrCode(), e.getRequestId());
            throw cloudException("create", e, classifyCreate(e));
        }
    }

    @Override
    public CloudInstanceView describe(String cloudInstanceId) {
        DescribeContainerGroupsRequest request = new DescribeContainerGroupsRequest();
        request.setRegionId(properties.getRegionId());
        request.setContainerGroupIds(toContainerGroupIdsJson(cloudInstanceId));

        try {
            DescribeContainerGroupsResponse response = client.getAcsResponse(request);
            if (response.getContainerGroups() == null || response.getContainerGroups().isEmpty()) {
                return new CloudInstanceView(cloudInstanceId, "NotFound", null);
            }
            DescribeContainerGroupsResponse.ContainerGroup group = response.getContainerGroups().get(0);
            if (group == null || group.getStatus() == null) {
                return new CloudInstanceView(cloudInstanceId, "NotFound", "empty container group");
            }
            return new CloudInstanceView(cloudInstanceId, group.getStatus(), buildStatusDetail(group));
        } catch (ClientException e) {
            if ("InvalidParameter.NotFound".equalsIgnoreCase(e.getErrCode())
                    || "ContainerGroupNotFound".equalsIgnoreCase(e.getErrCode())) {
                return new CloudInstanceView(cloudInstanceId, "NotFound", e.getErrMsg());
            }
            log.error("ECI describe failed cloudInstanceId={} errCode={} requestId={}",
                    cloudInstanceId, e.getErrCode(), e.getRequestId());
            throw cloudException("describe", e, CloudOperationKind.RETRYABLE);
        }
    }

    @Override
    public String fetchContainerLog(String cloudInstanceId, int tailLines) {
        if (!StringUtils.hasText(cloudInstanceId)) {
            return "";
        }
        int tail = tailLines > 0 ? tailLines : 1000;
        try {
            String containerName = resolvePrimaryContainerName(cloudInstanceId);
            if (!StringUtils.hasText(containerName)) {
                log.warn("ECI fetchContainerLog skipped: no container name cloudInstanceId={}", cloudInstanceId);
                return "";
            }
            DescribeContainerLogRequest request = new DescribeContainerLogRequest();
            request.setRegionId(properties.getRegionId());
            request.setContainerGroupId(cloudInstanceId);
            request.setContainerName(containerName);
            request.setTail(tail);
            DescribeContainerLogResponse response = client.getAcsResponse(request);
            String content = response != null ? response.getContent() : null;
            log.info("ECI fetchContainerLog ok cloudInstanceId={} containerName={} tail={} chars={}",
                    cloudInstanceId, containerName, tail, content != null ? content.length() : 0);
            return content != null ? content : "";
        } catch (ClientException e) {
            log.warn("ECI fetchContainerLog failed cloudInstanceId={} errCode={} requestId={}: {}",
                    cloudInstanceId, e.getErrCode(), e.getRequestId(), e.getErrMsg());
            return "fetchContainerLog failed: " + e.getErrCode() + " " + e.getErrMsg();
        } catch (RuntimeException e) {
            log.warn("ECI fetchContainerLog failed cloudInstanceId={}: {}", cloudInstanceId, e.getMessage());
            return "fetchContainerLog failed: " + e.getMessage();
        }
    }

    @Override
    public void delete(String cloudInstanceId) {
        DeleteContainerGroupRequest request = new DeleteContainerGroupRequest();
        request.setRegionId(properties.getRegionId());
        request.setContainerGroupId(cloudInstanceId);

        try {
            long start = System.currentTimeMillis();
            client.getAcsResponse(request);
            log.info("ECI delete ok cloudInstanceId={} elapsedMs={}",
                    cloudInstanceId, System.currentTimeMillis() - start);
        } catch (ClientException e) {
            if ("InvalidParameter.NotFound".equalsIgnoreCase(e.getErrCode())
                    || "ContainerGroupNotFound".equalsIgnoreCase(e.getErrCode())) {
                log.info("ECI delete skipped, already gone cloudInstanceId={}", cloudInstanceId);
                return;
            }
            log.error("ECI delete failed cloudInstanceId={} errCode={} requestId={}",
                    cloudInstanceId, e.getErrCode(), e.getRequestId());
            throw cloudException("delete", e, CloudOperationKind.RETRYABLE);
        }
    }

    private static CloudOperationException cloudException(
            String operation, ClientException exception, CloudOperationKind kind) {
        String errorCode = isCredentialResolutionFailure(exception)
                ? "credential resolution failed"
                : exception.getErrCode();
        return new CloudOperationException(operation, errorCode, kind);
    }

    private static boolean isCredentialResolutionFailure(ClientException exception) {
        String message = exception.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("credential");
    }

    private static CloudOperationKind classifyCreate(ClientException exception) {
        return isCredentialResolutionFailure(exception)
                || REJECTED_CREATE_ERROR_CODES.contains(exception.getErrCode())
                ? CloudOperationKind.REJECTED
                : CloudOperationKind.UNKNOWN_OUTCOME;
    }

    /**
     * 从 DescribeContainerGroups 解析主容器名（取第一个 containers 项）。
     */
    private String resolvePrimaryContainerName(String cloudInstanceId) throws ClientException {
        DescribeContainerGroupsRequest request = new DescribeContainerGroupsRequest();
        request.setRegionId(properties.getRegionId());
        request.setContainerGroupIds(toContainerGroupIdsJson(cloudInstanceId));
        DescribeContainerGroupsResponse response = client.getAcsResponse(request);
        if (response.getContainerGroups() == null || response.getContainerGroups().isEmpty()) {
            return null;
        }
        DescribeContainerGroupsResponse.ContainerGroup group = response.getContainerGroups().get(0);
        if (group == null || group.getContainers() == null || group.getContainers().isEmpty()) {
            return group != null ? group.getContainerGroupName() : null;
        }
        DescribeContainerGroupsResponse.ContainerGroup.Container container = group.getContainers().get(0);
        if (container != null && StringUtils.hasText(container.getName())) {
            return container.getName();
        }
        return group.getContainerGroupName();
    }

    /**
     * 汇总 exitCode / reason / 近期 events，供 failureReason 短文案使用（调用方再截断）。
     */
    static String buildStatusDetail(DescribeContainerGroupsResponse.ContainerGroup group) {
        if (group == null) {
            return null;
        }
        StringJoiner joiner = new StringJoiner("; ");
        if (group.getContainers() != null) {
            for (DescribeContainerGroupsResponse.ContainerGroup.Container c : group.getContainers()) {
                if (c == null) {
                    continue;
                }
                DescribeContainerGroupsResponse.ContainerGroup.Container.ContainerState state = c.getCurrentState();
                if (state == null) {
                    state = c.getPreviousState();
                }
                if (state == null) {
                    continue;
                }
                StringBuilder part = new StringBuilder();
                if (StringUtils.hasText(c.getName())) {
                    part.append(c.getName()).append(' ');
                }
                if (state.getExitCode() != null) {
                    part.append("exitCode=").append(state.getExitCode());
                }
                if (StringUtils.hasText(state.getReason())) {
                    if (part.length() > 0 && !part.toString().endsWith(" ")) {
                        part.append(' ');
                    }
                    part.append("reason=").append(state.getReason());
                }
                if (StringUtils.hasText(state.getMessage())) {
                    if (part.length() > 0) {
                        part.append(' ');
                    }
                    part.append("msg=").append(state.getMessage());
                }
                if (part.length() > 0) {
                    joiner.add(part.toString().trim());
                }
            }
        }
        if (group.getEvents() != null && !group.getEvents().isEmpty()) {
            int from = Math.max(0, group.getEvents().size() - 3);
            for (int i = from; i < group.getEvents().size(); i++) {
                DescribeContainerGroupsResponse.ContainerGroup.Event event = group.getEvents().get(i);
                if (event == null) {
                    continue;
                }
                StringBuilder part = new StringBuilder("event");
                if (StringUtils.hasText(event.getReason())) {
                    part.append('=').append(event.getReason());
                }
                if (StringUtils.hasText(event.getMessage())) {
                    part.append(':').append(event.getMessage());
                }
                if (part.length() > "event".length()) {
                    joiner.add(part.toString());
                }
            }
        }
        String detail = joiner.toString();
        return StringUtils.hasText(detail) ? detail : null;
    }

    /**
     * 将 Memory 类型 EmptyDir 挂到 {@code /dev/shm}，以放大 ECI 默认 64Mi 共享内存。
     */
    static void attachShmVolume(
            CreateContainerGroupRequest request,
            CreateContainerGroupRequest.Container container,
            String shmSize) {
        if (!StringUtils.hasText(shmSize)) {
            return;
        }
        CreateContainerGroupRequest.Volume volume = new CreateContainerGroupRequest.Volume();
        volume.setName(ShmSize.VOLUME_NAME);
        volume.setType(ShmSize.VOLUME_TYPE);
        volume.setEmptyDirVolumeMedium(ShmSize.MEDIUM_MEMORY);
        request.setVolumes(List.of(volume));
        // aliyun-java-sdk-eci 1.3.3 的 Volume 只有 Medium，没有 SizeLimit setter；
        // OpenAPI 仍认 Volume.N.EmptyDirVolume.SizeLimit，须单独写入查询参数。
        request.putQueryParameter("Volume.1.EmptyDirVolume.SizeLimit", shmSize.trim());

        CreateContainerGroupRequest.Container.VolumeMount mount =
                new CreateContainerGroupRequest.Container.VolumeMount();
        mount.setName(ShmSize.VOLUME_NAME);
        mount.setMountPath(ShmSize.MOUNT_PATH);
        container.setVolumeMounts(List.of(mount));
    }

    /**
     * 将 cloudInstanceId 编码为 Describe API 所需的 JSON 数组字符串。
     * 使用 Jackson 序列化，避免手工拼接导致的 JSON 注入 / 畸形。
     */
    static String toContainerGroupIdsJson(String cloudInstanceId) {
        if (!StringUtils.hasText(cloudInstanceId)) {
            throw new IllegalArgumentException("cloudInstanceId must not be blank");
        }
        try {
            return JSON.writeValueAsString(List.of(cloudInstanceId));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化 ContainerGroupIds 失败", e);
        }
    }

    /** 将显示名规范为 ECI 可接受的名称，并附加短随机后缀。 */
    static String sanitizeName(String name) {
        return sanitizeName(name, UUID.randomUUID().toString());
    }

    /** 将显示名规范化后附加由本地实例 token 派生的稳定后缀。 */
    static String sanitizeName(String name, String clientToken) {
        String base = sanitizeNameBase(name);
        String suffix = UUID.nameUUIDFromBytes(clientToken.getBytes(StandardCharsets.UTF_8))
                .toString().substring(0, 8);
        return base + "-" + suffix;
    }

    private static String sanitizeNameBase(String name) {
        String base = StringUtils.hasText(name) ? name : "eci-demo";
        String sanitized = base.toLowerCase().replaceAll("[^a-z0-9-]", "-");
        sanitized = sanitized.replaceAll("-{2,}", "-");
        sanitized = sanitized.replaceAll("^-+|-+$", "");
        if (sanitized.isEmpty()) {
            sanitized = "eci-demo";
        }
        if (sanitized.length() > 40) {
            sanitized = sanitized.substring(0, 40).replaceAll("-+$", "");
            if (sanitized.isEmpty()) {
                sanitized = "eci-demo";
            }
        }
        if (!sanitized.matches("^[a-z].*")) {
            sanitized = "e" + sanitized;
        }
        return sanitized;
    }
}
