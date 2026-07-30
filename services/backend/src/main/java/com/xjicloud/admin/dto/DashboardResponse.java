package com.xjicloud.admin.dto;

import java.util.Map;

public record DashboardResponse(
        long workerCount,
        long onlineWorkers,
        long queueDepth,
        long runningJobs,
        long completedJobsToday,
        long failedJobs
) {
}
