package com.xjicloud.eci.credential;

/**
 * 记录测通调用，CI 不连云。不是 SUT。
 */
public final class RecordingRuntimeCredentialProbe implements RuntimeCredentialProbe {

    private int eciCalls;
    private int ossCalls;
    private String lastEciAccessKeyId;
    private String lastEciAccessKeySecret;
    private String lastOssAccessKeyId;
    private String lastOssAccessKeySecret;
    private RuntimeException eciFailure;
    private RuntimeException ossFailure;

    @Override
    public void probeEci(String accessKeyId, String accessKeySecret) {
        eciCalls++;
        lastEciAccessKeyId = accessKeyId;
        lastEciAccessKeySecret = accessKeySecret;
        if (eciFailure != null) {
            throw eciFailure;
        }
    }

    @Override
    public void probeOss(String accessKeyId, String accessKeySecret) {
        ossCalls++;
        lastOssAccessKeyId = accessKeyId;
        lastOssAccessKeySecret = accessKeySecret;
        if (ossFailure != null) {
            throw ossFailure;
        }
    }

    public int eciCalls() {
        return eciCalls;
    }

    public int ossCalls() {
        return ossCalls;
    }

    public String lastEciAccessKeyId() {
        return lastEciAccessKeyId;
    }

    public String lastEciAccessKeySecret() {
        return lastEciAccessKeySecret;
    }

    public String lastOssAccessKeyId() {
        return lastOssAccessKeyId;
    }

    public String lastOssAccessKeySecret() {
        return lastOssAccessKeySecret;
    }

    public void failEci(RuntimeException failure) {
        this.eciFailure = failure;
    }

    public void failOss(RuntimeException failure) {
        this.ossFailure = failure;
    }

    public void reset() {
        eciCalls = 0;
        ossCalls = 0;
        lastEciAccessKeyId = null;
        lastEciAccessKeySecret = null;
        lastOssAccessKeyId = null;
        lastOssAccessKeySecret = null;
        eciFailure = null;
        ossFailure = null;
    }
}
