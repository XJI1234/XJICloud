package com.xjicloud.eci.credential;

/**
 * 用临时 STATIC_AK 客户端测通。失败抛异常，由 Tester 转成 ok=false。
 */
public interface RuntimeCredentialProbe {

    void probeEci(String accessKeyId, String accessKeySecret);

    void probeOss(String accessKeyId, String accessKeySecret);
}
