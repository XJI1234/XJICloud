package com.xjicloud.eci.provider;

public record CloudInstanceView(
        String cloudInstanceId,
        String status,
        String statusDetail
) {
    public boolean isRunning() {
        return status != null && "Running".equalsIgnoreCase(status);
    }

    public boolean isFailed() {
        if (status == null) {
            return false;
        }
        return "Failed".equalsIgnoreCase(status)
                || "ScheduleFailed".equalsIgnoreCase(status)
                || "CreateFailed".equalsIgnoreCase(status);
    }

    public boolean isTerminatedOrMissing() {
        if (status == null) {
            return true;
        }
        return "Deleted".equalsIgnoreCase(status)
                || "Terminated".equalsIgnoreCase(status)
                || "NotFound".equalsIgnoreCase(status);
    }
}
