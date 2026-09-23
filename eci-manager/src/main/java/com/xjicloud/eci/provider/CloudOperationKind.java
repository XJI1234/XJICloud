package com.xjicloud.eci.provider;

/** 云 API 调用的结果确定性，用于决定是否可安全重试。 */
public enum CloudOperationKind {
    REJECTED,
    UNKNOWN_OUTCOME,
    RETRYABLE
}
