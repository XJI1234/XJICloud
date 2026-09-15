package com.xjicloud.eci.credential;

import com.aliyuncs.IAcsClient;

/**
 * 只用 region + AccessKey 建临时 ECI 客户端，禁止默认链 / IMDS。
 */
public interface StaticAkAcsClientFactory {

    IAcsClient create(String regionId, String accessKeyId, String accessKeySecret);
}
