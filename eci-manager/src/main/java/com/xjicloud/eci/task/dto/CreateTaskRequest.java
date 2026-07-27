package com.xjicloud.eci.task.dto;

import java.util.List;
import java.util.Map;

/**
 * 创建任务请求体。{@code image} 必填；其余可选，未传时使用 {@code eci.default-*} 配置。
 */
public class CreateTaskRequest {

    private String name;
    /** 必填：完整镜像地址。 */
    private String image;
    private List<String> command;
    private List<String> args;
    private Double cpu;
    private Double memory;
    private String instanceType;
    private Integer gpu;
    /** Never / OnFailure / Always。 */
    private String restartPolicy;
    /** 正整数；未传则用 {@code eci.default-max-runtime-sec}。 */
    private Integer maxRuntimeSec;
    private Map<String, String> env;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public List<String> getCommand() {
        return command;
    }

    public void setCommand(List<String> command) {
        this.command = command;
    }

    public List<String> getArgs() {
        return args;
    }

    public void setArgs(List<String> args) {
        this.args = args;
    }

    public Double getCpu() {
        return cpu;
    }

    public void setCpu(Double cpu) {
        this.cpu = cpu;
    }

    public Double getMemory() {
        return memory;
    }

    public void setMemory(Double memory) {
        this.memory = memory;
    }

    public String getInstanceType() {
        return instanceType;
    }

    public void setInstanceType(String instanceType) {
        this.instanceType = instanceType;
    }

    public Integer getGpu() {
        return gpu;
    }

    public void setGpu(Integer gpu) {
        this.gpu = gpu;
    }

    public String getRestartPolicy() {
        return restartPolicy;
    }

    public void setRestartPolicy(String restartPolicy) {
        this.restartPolicy = restartPolicy;
    }

    public Integer getMaxRuntimeSec() {
        return maxRuntimeSec;
    }

    public void setMaxRuntimeSec(Integer maxRuntimeSec) {
        this.maxRuntimeSec = maxRuntimeSec;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public void setEnv(Map<String, String> env) {
        this.env = env;
    }
}
