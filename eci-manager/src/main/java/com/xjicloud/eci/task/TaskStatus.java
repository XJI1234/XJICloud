package com.xjicloud.eci.task;

/**
 * 任务本地权威状态机枚举。
 * <pre>
 * PENDING → STARTING → RUNNING → SUCCEEDED | FAILED
 * STARTING → SUCCEEDED | FAILED
 * PENDING / STARTING / RUNNING → CANCELLING → CANCELLED
 * PENDING / STARTING / RUNNING → FAILED（创建失败、超时、云异常）
 * </pre>
 */
public enum TaskStatus {
    /** 已受理，尚未创建实例。 */
    PENDING,
    /** 正在创建实例或等待 Running。 */
    STARTING,
    /** 实例已 Running，任务进程执行中。 */
    RUNNING,
    /** 云侧成功结束且自动清理完成。 */
    SUCCEEDED,
    /** 创建失败、进程失败、超时或其他不可恢复错误。 */
    FAILED,
    /** 调用方已请求取消，持久 DELETE 仍在收敛。 */
    CANCELLING,
    /** 调用方取消且关联实例已删除或不存在。 */
    CANCELLED
}
