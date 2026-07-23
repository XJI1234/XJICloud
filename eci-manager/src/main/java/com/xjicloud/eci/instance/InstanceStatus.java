package com.xjicloud.eci.instance;

/**
 * 本地权威状态机枚举（与云侧字符串状态区分）。
 * <pre>
 * CREATING → RUNNING | FAILED
 * RUNNING  → STOPPING → TERMINATED | FAILED
 * </pre>
 */
public enum InstanceStatus {
    /** 已受理创建，正在调云或等待 Running。 */
    CREATING,
    /** 云侧已 Running（或 Mock 成功）。 */
    RUNNING,
    /** 已受理删除请求。 */
    STOPPING,
    /** 云侧已删除，终态。 */
    TERMINATED,
    /** 创建或删除失败。 */
    FAILED
}
