package com.xjicloud.eci.provider;

/**
 * 云侧实例视图，用于轮询与 refresh 回写。
 *
 * @param status       云侧状态字符串（如 Running、Pending、NotFound）
 * @param statusDetail 补充说明（可选）
 */
public record CloudInstanceView(
        String cloudInstanceId,
        String status,
        String statusDetail
) {
    /** 是否已进入 Running。 */
    public boolean isRunning() {
        return status != null && "Running".equalsIgnoreCase(status);
    }

    /** 是否为任务成功终态（容器组 Succeeded）。 */
    public boolean isSucceeded() {
        return status != null && "Succeeded".equalsIgnoreCase(status);
    }

    /** 是否为创建/调度失败类终态。 */
    public boolean isFailed() {
        if (status == null) {
            return false;
        }
        return "Failed".equalsIgnoreCase(status)
                || "ScheduleFailed".equalsIgnoreCase(status)
                || "CreateFailed".equalsIgnoreCase(status);
    }

    /** 是否已删除或不存在。 */
    public boolean isTerminatedOrMissing() {
        return status != null && ("Deleted".equalsIgnoreCase(status)
                || "Terminated".equalsIgnoreCase(status)
                || "NotFound".equalsIgnoreCase(status));
    }
}
