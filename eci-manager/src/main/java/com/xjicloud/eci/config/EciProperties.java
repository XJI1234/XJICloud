package com.xjicloud.eci.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 绑定配置前缀 {@code eci.} 的属性。
 * <p>
 * 可通过 {@code application.yml}、profile 文件或环境变量覆盖（如 {@code ECI_API_KEY}）。
 */
@ConfigurationProperties(prefix = "eci")
public class EciProperties {

    /** 为 true 时使用 MockProvider，不调用阿里云。 */
    private boolean mock = true;
    /** HTTP 鉴权密钥，对应请求头 X-Api-Key。 */
    private String apiKey = "";
    private String accessKeyId = "";
    private String accessKeySecret = "";
    private String regionId = "cn-hangzhou";
    private String vpcId = "";
    /** 真实模式创建 ECI 必填。 */
    private String vswitchId = "";
    /** 真实模式创建 ECI 必填。 */
    private String securityGroupId = "";
    /** 请求未传 image 时的默认镜像。 */
    private String defaultImage = "nginx:alpine";
    private double defaultCpu = 1.0;
    private double defaultMemory = 2.0;
    /** 等待 Running / 删除完成的超时秒数。 */
    private int createTimeoutSec = 300;
    /** 后台轮询云状态的间隔毫秒。 */
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
