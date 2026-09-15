package com.xjicloud.eci.credential;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.profile.DefaultProfile;
import org.springframework.stereotype.Component;

/**
 * {@code DefaultProfile.getProfile(region, ak, sk)}，禁止默认链。
 */
@Component
public class DefaultStaticAkAcsClientFactory implements StaticAkAcsClientFactory {

    @Override
    public IAcsClient create(String regionId, String accessKeyId, String accessKeySecret) {
        if (!new AccessKeyPair(accessKeyId, accessKeySecret).isComplete()) {
            throw new IllegalArgumentException("accessKeyId 与 accessKeySecret 必须成对非空");
        }
        DefaultProfile profile = DefaultProfile.getProfile(
                regionId, accessKeyId.trim(), accessKeySecret.trim());
        return new DefaultAcsClient(profile);
    }
}
