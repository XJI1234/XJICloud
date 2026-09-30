package com.xjicloud.worker;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.xjicloud.auth.JwtService;
import com.xjicloud.common.BusinessException;
import com.xjicloud.config.WorkerProperties;
import com.xjicloud.job.DatasetAssetRepository;
import com.xjicloud.job.TrainingJobRepository;
import com.xjicloud.job.TrainingJobService;
import com.xjicloud.oss.OssStorageService;
import com.xjicloud.queue.RedisQueueService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkerExecutionPathDisabledTest {

    @Test
    void legacyProgressCompletionAndFailureCallbacksCannotChangeTrainingJobs() {
        TrainingJobService jobService = mock(TrainingJobService.class);
        WorkerService workerService = new WorkerService(
                mock(WorkerNodeRepository.class),
                mock(TrainingJobRepository.class),
                mock(DatasetAssetRepository.class),
                jobService,
                mock(OssStorageService.class),
                mock(RedisQueueService.class),
                mock(JwtService.class),
                new WorkerProperties("test-secret", 60, 0)
        );

        assertThatThrownBy(() -> workerService.reportProgress(null, UUID.randomUUID(), null))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getStatus().value()).isEqualTo(410);
                });
        assertThatThrownBy(() -> workerService.completeJob(null, UUID.randomUUID(), null))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getStatus().value()).isEqualTo(410);
                });
        assertThatThrownBy(() -> workerService.failJob(null, UUID.randomUUID(), null))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getStatus().value()).isEqualTo(410);
                });

        verifyNoInteractions(jobService);
    }
}
