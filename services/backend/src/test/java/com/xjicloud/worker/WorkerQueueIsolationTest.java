package com.xjicloud.worker;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.xjicloud.auth.JwtService;
import com.xjicloud.config.WorkerProperties;
import com.xjicloud.job.DatasetAssetRepository;
import com.xjicloud.job.TrainingJobRepository;
import com.xjicloud.job.TrainingJobService;
import com.xjicloud.oss.OssStorageService;
import com.xjicloud.queue.RedisQueueService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkerQueueIsolationTest {

    @Test
    void pollingLegacyWorkerEndpointDoesNotConsumeTrainingQueue() {
        WorkerNodeRepository workerRepository = mock(WorkerNodeRepository.class);
        TrainingJobRepository jobRepository = mock(TrainingJobRepository.class);
        DatasetAssetRepository assetRepository = mock(DatasetAssetRepository.class);
        TrainingJobService jobService = mock(TrainingJobService.class);
        OssStorageService ossStorageService = mock(OssStorageService.class);
        RedisQueueService queueService = mock(RedisQueueService.class);
        JwtService jwtService = mock(JwtService.class);
        WorkerService workerService = new WorkerService(
                workerRepository,
                jobRepository,
                assetRepository,
                jobService,
                ossStorageService,
                queueService,
                jwtService,
                new WorkerProperties("test-secret", 60, 0)
        );
        WorkerNode worker = new WorkerNode();
        worker.setId(UUID.randomUUID());
        worker.setName("legacy-worker");
        worker.setStatus(WorkerStatus.IDLE);
        worker.setLastHeartbeat(Instant.now());

        workerService.pollNextJob(worker);

        verify(queueService, never()).dequeueBlocking(org.mockito.ArgumentMatchers.anyLong());
    }
}
