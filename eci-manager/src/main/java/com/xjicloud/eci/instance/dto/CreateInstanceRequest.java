package com.xjicloud.eci.instance.dto;

import java.util.Map;

/**
 * 创建实例请求体；字段均可选，未传时使用 {@code eci.default-*} 配置。
 */
public class CreateInstanceRequest {

    /** 显示名称；未传则自动生成 demo-xxxxxxxx。 */
    private String name;
    /** 容器镜像；未传则用默认镜像。 */
    private String image;
    private Double cpu;
    private Double memory;
    /** 透传到容器的环境变量（如日后 Worker 对接用）。 */
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

    public Map<String, String> getEnv() {
        return env;
    }

    public void setEnv(Map<String, String> env) {
        this.env = env;
    }
}
