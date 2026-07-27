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
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.eci.config.EciProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 阿里云 ECI OpenAPI 实现：CreateContainerGroup / DescribeContainerGroups / DeleteContainerGroup。
 * <p>
 * 云凭证走 SDK 内置链（ECS RAM 角色 / 环境变量等），不使用 yml 长期 AccessKey。
 * 日志只记录实例 ID 与错误码，不打印凭证 / Api-Key。
 */
public class AliyunEciProvider implements EciProvider {

    private static final Logger log = LoggerFactory.getLogger(AliyunEciProvider.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String CREDENTIAL_HINT =
            "无法解析阿里云凭证：生产请为 ECS 绑定 RAM 角色（可配 eci.ecs-ram-role-name）；"
                    + "本机请设置 ALIBABA_CLOUD_ACCESS_KEY_ID/SECRET（及 SECURITY_TOKEN）临时环境变量。"
                    + "非 ECS 上出现连接 100.100.100.200 超时属预期。";

    private final EciProperties properties;
    private final IAcsClient client;

    public AliyunEciProvider(EciProperties properties) {
        this.properties = properties;
        String regionId = properties.getRegionId();
        String roleName = properties.getEcsRamRoleName();
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
            // 启动期失败快：非 ECS 无 env 时可能 IMDS 超时后抛错
            creds.getCredentials();
        } catch (ClientException e) {
            throw new IllegalStateException(CREDENTIAL_HINT + " 原因: " + e.getErrMsg(), e);
        } catch (RuntimeException e) {
            throw new IllegalStateException(CREDENTIAL_HINT + " 原因: " + e.getMessage(), e);
        }
        DefaultProfile profile = DefaultProfile.getProfile(regionId);
        this.client = new DefaultAcsClient(profile, creds);
        log.info("ECI Aliyun client ready credentialMode={} region={}", credentialMode, regionId);
    }

    @Override
    public String create(CreateSpec spec) {
        CreateContainerGroupRequest request = new CreateContainerGroupRequest();
        request.setRegionId(properties.getRegionId());
        request.setSecurityGroupId(properties.getSecurityGroupId());
        request.setVSwitchId(properties.getVswitchId());
        if (StringUtils.hasText(spec.instanceType())) {
            request.setInstanceType(spec.instanceType().trim());
        }
        if (StringUtils.hasText(spec.restartPolicy())) {
            request.setRestartPolicy(spec.restartPolicy().trim());
        }
        // 容器组名与容器名共用同一规范化名称，避免两次随机后缀不一致
        String resourceName = sanitizeName(spec.name());
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

        request.setContainers(List.of(container));

        try {
            long start = System.currentTimeMillis();
            CreateContainerGroupResponse response = client.getAcsResponse(request);
            log.info("ECI create ok cloudInstanceId={} instanceType={} gpu={} restartPolicy={} elapsedMs={}",
                    response.getContainerGroupId(),
                    spec.instanceType(),
                    spec.gpu(),
                    spec.restartPolicy(),
                    System.currentTimeMillis() - start);
            return response.getContainerGroupId();
        } catch (ClientException e) {
            log.error("ECI create failed errCode={} requestId={}", e.getErrCode(), e.getRequestId());
            throw new IllegalStateException("阿里云 ECI 创建失败: " + e.getErrCode() + " " + e.getErrMsg(), e);
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
            return new CloudInstanceView(cloudInstanceId, group.getStatus(), null);
        } catch (ClientException e) {
            if ("InvalidParameter.NotFound".equalsIgnoreCase(e.getErrCode())
                    || "ContainerGroupNotFound".equalsIgnoreCase(e.getErrCode())) {
                return new CloudInstanceView(cloudInstanceId, "NotFound", e.getErrMsg());
            }
            log.error("ECI describe failed cloudInstanceId={} errCode={} requestId={}",
                    cloudInstanceId, e.getErrCode(), e.getRequestId());
            throw new IllegalStateException("阿里云 ECI 查询失败: " + e.getErrCode() + " " + e.getErrMsg(), e);
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
            throw new IllegalStateException("阿里云 ECI 删除失败: " + e.getErrCode() + " " + e.getErrMsg(), e);
        }
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

    /**
     * 将显示名规范为 ECI 可接受的名称（小写、数字、连字符），并附加短随机后缀保证唯一。
     * 同一 create 调用内应对返回值只使用一次，勿重复调用以免后缀不同。
     */
    static String sanitizeName(String name) {
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
        return sanitized + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
