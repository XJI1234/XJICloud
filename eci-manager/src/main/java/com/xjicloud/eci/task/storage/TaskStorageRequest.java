package com.xjicloud.eci.task.storage;

import java.util.List;

/**
 * {@code POST /tasks} 请求体中的可选 {@code storage} 块。
 * <p>
 * 由 {@link TaskStorageResolver} 校验并签发 URL；未启用 OSS 时出现本块即 400。
 */
public class TaskStorageRequest {

    /** {@code test} | {@code keys}；{@code dataset} 一期拒绝。 */
    private String mode;
    /** 预留（dataset）；一期未用。 */
    private String jobId;
    /** keys 模式必填：输出对象完整 OSS key。 */
    private String outputKey;
    /** test 模式输出文件名；默认 {@code output.txt}。 */
    private String outputFileName;
    /** 可选输入对象 key 列表；非空时签发 GET 并写入 {@code XJI_INPUT_FILES_JSON}。 */
    private List<String> inputKeys;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getOutputKey() {
        return outputKey;
    }

    public void setOutputKey(String outputKey) {
        this.outputKey = outputKey;
    }

    public String getOutputFileName() {
        return outputFileName;
    }

    public void setOutputFileName(String outputFileName) {
        this.outputFileName = outputFileName;
    }

    public List<String> getInputKeys() {
        return inputKeys;
    }

    public void setInputKeys(List<String> inputKeys) {
        this.inputKeys = inputKeys;
    }
}
