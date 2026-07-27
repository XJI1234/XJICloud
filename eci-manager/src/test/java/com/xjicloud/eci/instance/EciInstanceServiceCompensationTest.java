package com.xjicloud.eci.instance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.lifecycle.AsyncLifecycleWorker;
import com.xjicloud.eci.provider.CloudInstanceView;
import com.xjicloud.eci.provider.EciProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 启动补偿 {@link EciInstanceService#compensateOne} 分支覆盖。
 */
@ExtendWith(MockitoExtension.class)
class EciInstanceServiceCompensationTest {

    @Mock
    private EciInstanceRepository repository;
    @Mock
    private EciProvider eciProvider;
    @Mock
    private AsyncLifecycleWorker lifecycleWorker;
    @Mock
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUpTx() {
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
    }

    /**
     * {@code refreshTarget} 作为 self，供 compensateOne 经代理路径调用 refresh；
     * 单测无 Spring 代理时，两者共享同一组 mock 即可。
     */
    private EciInstanceService newService(EciInstanceService refreshTarget) {
        return new EciInstanceService(
                repository,
                new EciProperties(),
                eciProvider,
                lifecycleWorker,
                new ObjectMapper(),
                transactionManager,
                null,
                refreshTarget);
    }

    private EciInstanceService serviceWithSelfRefresh() {
        EciInstanceService refreshTarget = newService(null);
        return newService(refreshTarget);
    }

    private static EciInstanceEntity baseEntity(UUID id, InstanceStatus status, String cloudId) {
        EciInstanceEntity entity = new EciInstanceEntity();
        entity.setId(id);
        entity.setName("demo");
        entity.setImage("nginx:alpine");
        entity.setCpu(1);
        entity.setMemory(2);
        entity.setStatus(status);
        entity.setCloudInstanceId(cloudId);
        entity.setEnvJson("{}");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    @Test
    void creatingWithoutCloudIdMarksFailedLocal() {
        UUID id = UUID.randomUUID();
        EciInstanceEntity entity = baseEntity(id, InstanceStatus.CREATING, null);
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompensationOutcome outcome = newService(null).compensateOne(id);

        assertThat(outcome).isEqualTo(CompensationOutcome.FAILED_LOCAL);
        ArgumentCaptor<EciInstanceEntity> captor = ArgumentCaptor.forClass(EciInstanceEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(InstanceStatus.FAILED);
        assertThat(captor.getValue().getFailureReason()).contains("进程中断");
        verify(lifecycleWorker, never()).resumeCreatePoll(any(), any());
    }

    @Test
    void creatingWithCloudIdStillPendingResumesPoll() {
        UUID id = UUID.randomUUID();
        EciInstanceEntity entity = baseEntity(id, InstanceStatus.CREATING, "eci-1");
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(eciProvider.describe("eci-1"))
                .thenReturn(new CloudInstanceView("eci-1", "Pending", null));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompensationOutcome outcome = serviceWithSelfRefresh().compensateOne(id);

        assertThat(outcome).isEqualTo(CompensationOutcome.RESUMED_CREATE);
        verify(lifecycleWorker).resumeCreatePoll(eq(id), eq("eci-1"));
        verify(lifecycleWorker, never()).scheduleCreate(any(), any());
    }

    @Test
    void creatingWithCloudIdNotFoundDoesNotResume() {
        UUID id = UUID.randomUUID();
        EciInstanceEntity entity = baseEntity(id, InstanceStatus.CREATING, "eci-gone");
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(eciProvider.describe("eci-gone"))
                .thenReturn(new CloudInstanceView("eci-gone", "NotFound", null));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompensationOutcome outcome = serviceWithSelfRefresh().compensateOne(id);

        assertThat(outcome).isEqualTo(CompensationOutcome.REFRESHED_ONLY);
        verify(lifecycleWorker, never()).resumeCreatePoll(any(), any());
        assertThat(entity.getStatus()).isEqualTo(InstanceStatus.TERMINATED);
    }

    @Test
    void stoppingWithCloudIdStillVisibleResumesDelete() {
        UUID id = UUID.randomUUID();
        EciInstanceEntity entity = baseEntity(id, InstanceStatus.STOPPING, "eci-2");
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(eciProvider.describe("eci-2"))
                .thenReturn(new CloudInstanceView("eci-2", "Running", null));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompensationOutcome outcome = serviceWithSelfRefresh().compensateOne(id);

        assertThat(outcome).isEqualTo(CompensationOutcome.RESUMED_DELETE);
        verify(lifecycleWorker).resumeDelete(id);
    }

    @Test
    void stoppingNotFoundDoesNotResume() {
        UUID id = UUID.randomUUID();
        EciInstanceEntity entity = baseEntity(id, InstanceStatus.STOPPING, "eci-gone");
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(eciProvider.describe("eci-gone"))
                .thenReturn(new CloudInstanceView("eci-gone", "NotFound", null));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompensationOutcome outcome = serviceWithSelfRefresh().compensateOne(id);

        assertThat(outcome).isEqualTo(CompensationOutcome.REFRESHED_ONLY);
        verify(lifecycleWorker, never()).resumeDelete(any());
        assertThat(entity.getStatus()).isEqualTo(InstanceStatus.TERMINATED);
    }

    @Test
    void runningOnlyRefreshes() {
        UUID id = UUID.randomUUID();
        EciInstanceEntity entity = baseEntity(id, InstanceStatus.RUNNING, "eci-3");
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(eciProvider.describe("eci-3"))
                .thenReturn(new CloudInstanceView("eci-3", "Running", null));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompensationOutcome outcome = serviceWithSelfRefresh().compensateOne(id);

        assertThat(outcome).isEqualTo(CompensationOutcome.REFRESHED_ONLY);
        verify(lifecycleWorker, never()).resumeCreatePoll(any(), any());
        verify(lifecycleWorker, never()).resumeDelete(any());
        verify(eciProvider).describe("eci-3");
    }

    @Test
    void stoppingWithoutCloudIdMarksTerminatedLocal() {
        UUID id = UUID.randomUUID();
        EciInstanceEntity entity = baseEntity(id, InstanceStatus.STOPPING, null);
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompensationOutcome outcome = newService(null).compensateOne(id);

        assertThat(outcome).isEqualTo(CompensationOutcome.TERMINATED_LOCAL);
        verify(lifecycleWorker, never()).resumeDelete(any());
        ArgumentCaptor<EciInstanceEntity> captor = ArgumentCaptor.forClass(EciInstanceEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(InstanceStatus.TERMINATED);
    }
}
