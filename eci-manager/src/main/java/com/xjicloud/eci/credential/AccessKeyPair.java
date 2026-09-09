package com.xjicloud.eci.credential;

import org.springframework.util.StringUtils;

/**
 * 一对 AccessKey。双方都有文本才算完整，才能走 STATIC_AK。
 */
public record AccessKeyPair(String accessKeyId, String accessKeySecret) {

    public boolean isComplete() {
        return StringUtils.hasText(accessKeyId) && StringUtils.hasText(accessKeySecret);
    }
}
