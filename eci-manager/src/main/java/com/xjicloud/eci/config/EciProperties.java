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
    /**
     * 轮换 HTTP Api-Key 后旧钥仍可用的秒数；{@code 0} 立即切断。默认 3600。
     */
    private int apiKeyOverlapSec = 3600;
    /**
     * 成对非空时 {@link com.xjicloud.eci.provider.AliyunEciProvider} 走 STATIC_AK。
     * 对应环境变量 {@code ECI_ACCESS_KEY_ID}（可由 {@code load-env.sh} 从 {@code ALIBABA_CLOUD_*} 映射）。
     */
    private String accessKeyId = "";
    /** 与 {@link #accessKeyId} 成对；对应 {@code ECI_ACCESS_KEY_SECRET}。 */
    private String accessKeySecret = "";
    /**
     * 可选。ECS 实例 RAM 角色名；有值时用 {@code InstanceProfileCredentialsProvider}。
     * 也可设环境变量 {@code ALIBABA_CLOUD_ECS_METADATA}。
     */
    private String ecsRamRoleName = "";
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
    /** 等待创建至 Running 的超时秒数。 */
    private int createTimeoutSec = 300;
    /** 等待删除完成（云侧消失）的超时秒数。 */
    private int deleteTimeoutSec = 300;
    /** 后台轮询云状态的间隔毫秒。 */
    private long pollIntervalMs = 3000L;
    /** 默认重启策略；任务/实例未指定时使用（Never / OnFailure / Always）。 */
    private String defaultRestartPolicy = "Never";
    /** 任务未指定 maxRuntimeSec 时的最大运行时长（秒）。 */
    private int defaultMaxRuntimeSec = 14400;
    /** 运行中任务轮询云终态的周期（秒）。 */
    private int taskPollIntervalSec = 10;
    /**
     * Mock 专用：创建后多少秒自动迁到终态；{@code <=0} 关闭（仅 {@code simulateStatus} 钩子）。
     */
    private int mockAutoCompleteSec = 3;
    /** Mock 自动终态：Succeeded 或 Failed。 */
    private String mockAutoCompleteStatus = "Succeeded";
    /**
     * 失败/超时时 DescribeContainerLog 拉取的尾部行数。
     */
    private int failureLogTailLines = 1000;
    /**
     * 失败落库后、发起删除前的取证宽限秒数；{@code 0} 关闭。
     */
    private int failureLogGraceSec = 90;

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

    public int getApiKeyOverlapSec() {
        return apiKeyOverlapSec;
    }

    public void setApiKeyOverlapSec(int apiKeyOverlapSec) {
        this.apiKeyOverlapSec = apiKeyOverlapSec;
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

    public String getEcsRamRoleName() {
        return ecsRamRoleName;
    }

    public void setEcsRamRoleName(String ecsRamRoleName) {
        this.ecsRamRoleName = ecsRamRoleName;
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

    public int getDeleteTimeoutSec() {
        return deleteTimeoutSec;
    }

    public void setDeleteTimeoutSec(int deleteTimeoutSec) {
        this.deleteTimeoutSec = deleteTimeoutSec;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public String getDefaultRestartPolicy() {
        return defaultRestartPolicy;
    }

    public void setDefaultRestartPolicy(String defaultRestartPolicy) {
        this.defaultRestartPolicy = defaultRestartPolicy;
    }

    public int getDefaultMaxRuntimeSec() {
        return defaultMaxRuntimeSec;
    }

    public void setDefaultMaxRuntimeSec(int defaultMaxRuntimeSec) {
        this.defaultMaxRuntimeSec = defaultMaxRuntimeSec;
    }

    public int getTaskPollIntervalSec() {
        return taskPollIntervalSec;
    }

    public void setTaskPollIntervalSec(int taskPollIntervalSec) {
        this.taskPollIntervalSec = taskPollIntervalSec;
    }

    public int getMockAutoCompleteSec() {
        return mockAutoCompleteSec;
    }

    public void setMockAutoCompleteSec(int mockAutoCompleteSec) {
        this.mockAutoCompleteSec = mockAutoCompleteSec;
    }

    public String getMockAutoCompleteStatus() {
        return mockAutoCompleteStatus;
    }

    public void setMockAutoCompleteStatus(String mockAutoCompleteStatus) {
        this.mockAutoCompleteStatus = mockAutoCompleteStatus;
    }

    public int getFailureLogTailLines() {
        return failureLogTailLines;
    }

    public void setFailureLogTailLines(int failureLogTailLines) {
        this.failureLogTailLines = failureLogTailLines;
    }

    public int getFailureLogGraceSec() {
        return failureLogGraceSec;
    }

    public void setFailureLogGraceSec(int failureLogGraceSec) {
        this.failureLogGraceSec = failureLogGraceSec;
    }
}
