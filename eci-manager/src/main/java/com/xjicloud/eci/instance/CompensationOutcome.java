package com.xjicloud.eci.instance;

/** 单条启动补偿结果，供 Runner 汇总日志。 */
public enum CompensationOutcome {
    /** 无 cloudId 的 CREATING → 本地 FAILED。 */
    FAILED_LOCAL,
    /** 仍 CREATING，已提交 resumeCreatePoll。 */
    RESUMED_CREATE,
    /** 仍 STOPPING，已提交 resumeDelete。 */
    RESUMED_DELETE,
    /** 仅 refresh（含已对齐终态 / RUNNING）。 */
    REFRESHED_ONLY,
    /** 无 cloudId 的 STOPPING → 本地 TERMINATED。 */
    TERMINATED_LOCAL,
    /** 记录不存在或非补偿范围。 */
    SKIPPED
}
