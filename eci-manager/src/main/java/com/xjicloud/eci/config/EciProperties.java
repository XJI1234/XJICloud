package com.xjicloud.eci.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eci")
public class EciProperties {

    private boolean mock = true;
    private String apiKey = "";
    private String accessKeyId = "";
    private String accessKeySecret = "";
    private String regionId = "cn-hangzhou";
    private String vpcId = "";
    private String vswitchId = "";
    private String securityGroupId = "";
    private String defaultImage = "nginx:alpine";
    private double defaultCpu = 1.0;
    private double defaultMemory = 2.0;
    private int createTimeoutSec = 300;
    private long pollIntervalMs = 3000L;

    public boolean isMock() {
        return mock;
    }

    public void setMock(boolean mock) {
        this.mock = mock;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getVpcId() {
        return vpcId;
    }

    public void setVpcId(String vpcId) {
        this.vpcId = vpcId;
    }

    public String getVswitchId() {
        return vswitchId;
    }

    public void setVswitchId(String vswitchId) {
        this.vswitchId = vswitchId;
    }

    public String getSecurityGroupId() {
        return securityGroupId;
    }

    public void setSecurityGroupId(String securityGroupId) {
        this.securityGroupId = securityGroupId;
    }

    public String getDefaultImage() {
        return defaultImage;
    }

    public void setDefaultImage(String defaultImage) {
        this.defaultImage = defaultImage;
    }

    public double getDefaultCpu() {
        return defaultCpu;
    }

    public void setDefaultCpu(double defaultCpu) {
        this.defaultCpu = defaultCpu;
    }

    public double getDefaultMemory() {
        return defaultMemory;
    }

    public void setDefaultMemory(double defaultMemory) {
        this.defaultMemory = defaultMemory;
    }

    public int getCreateTimeoutSec() {
        return createTimeoutSec;
    }

    public void setCreateTimeoutSec(int createTimeoutSec) {
        this.createTimeoutSec = createTimeoutSec;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }
}
