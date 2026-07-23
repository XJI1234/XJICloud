package com.xjicloud.eci.provider;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.eci.model.v20180808.CreateContainerGroupRequest;
import com.aliyuncs.eci.model.v20180808.CreateContainerGroupResponse;
import com.aliyuncs.eci.model.v20180808.DeleteContainerGroupRequest;
import com.aliyuncs.eci.model.v20180808.DescribeContainerGroupsRequest;
import com.aliyuncs.eci.model.v20180808.DescribeContainerGroupsResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import com.xjicloud.eci.config.EciProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AliyunEciProvider implements EciProvider {

    private static final Logger log = LoggerFactory.getLogger(AliyunEciProvider.class);

    private final EciProperties properties;
    private final IAcsClient client;

    public AliyunEciProvider(EciProperties properties) {
        this.properties = properties;
        DefaultProfile profile = DefaultProfile.getProfile(
                properties.getRegionId(),
                properties.getAccessKeyId(),
                properties.getAccessKeySecret());
        this.client = new DefaultAcsClient(profile);
    }

    @Override
    public String create(CreateSpec spec) {
        CreateContainerGroupRequest request = new CreateContainerGroupRequest();
        request.setRegionId(properties.getRegionId());
        request.setSecurityGroupId(properties.getSecurityGroupId());
        request.setVSwitchId(properties.getVswitchId());
        request.setContainerGroupName(sanitizeName(spec.name()));

        CreateContainerGroupRequest.Container container = new CreateContainerGroupRequest.Container();
        container.setName(sanitizeName(spec.name()));
        container.setImage(spec.image());
        container.setCpu((float) spec.cpu());
        container.setMemory((float) spec.memory());

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
            log.info("ECI create ok cloudInstanceId={} elapsedMs={}",
                    response.getContainerGroupId(), System.currentTimeMillis() - start);
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
        request.setContainerGroupIds("[\"" + cloudInstanceId + "\"]");

        try {
            DescribeContainerGroupsResponse response = client.getAcsResponse(request);
            if (response.getContainerGroups() == null || response.getContainerGroups().isEmpty()) {
                return new CloudInstanceView(cloudInstanceId, "NotFound", null);
            }
            DescribeContainerGroupsResponse.ContainerGroup group = response.getContainerGroups().get(0);
            return new CloudInstanceView(cloudInstanceId, group.getStatus(), group.getStatus());
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

    private static String sanitizeName(String name) {
        String base = StringUtils.hasText(name) ? name : "eci-demo";
        String sanitized = base.toLowerCase().replaceAll("[^a-z0-9-]", "-");
        if (sanitized.length() > 40) {
            sanitized = sanitized.substring(0, 40);
        }
        if (!sanitized.matches("^[a-z].*")) {
            sanitized = "e" + sanitized;
        }
        return sanitized + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
