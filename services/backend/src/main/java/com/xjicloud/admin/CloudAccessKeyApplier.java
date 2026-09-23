package com.xjicloud.admin;

import org.springframework.stereotype.Component;

public interface CloudAccessKeyApplier {

    void apply(String resource, String accessKeyId, String accessKeySecret);
}

@Component
class EciManagerApplier implements CloudAccessKeyApplier {

    private final EciManagerClient eciManagerClient;

    EciManagerApplier(EciManagerClient eciManagerClient) {
        this.eciManagerClient = eciManagerClient;
    }

    @Override
    public void apply(String resource, String accessKeyId, String accessKeySecret) {
        eciManagerClient.putRuntimeCredential(resource, accessKeyId, accessKeySecret);
    }
}
