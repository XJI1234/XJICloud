package com.xjicloud.job;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "xjicloud.eci-manager.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class TrainingDispatchScheduler {

    private final TrainingDispatchService dispatchService;

    public TrainingDispatchScheduler(TrainingDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @Scheduled(fixedDelayString = "${xjicloud.eci-manager.dispatch-delay-ms:1000}")
    public void dispatchNext() {
        dispatchService.dispatchNext();
    }

    @Scheduled(fixedDelayString = "${xjicloud.eci-manager.recovery-delay-ms:30000}")
    public void recoverPendingJobs() {
        dispatchService.recoverPendingJobs();
    }

    @Scheduled(fixedDelayString = "${xjicloud.eci-manager.poll-delay-ms:5000}")
    public void pollBoundJobs() {
        dispatchService.pollBoundJobs();
    }
}
