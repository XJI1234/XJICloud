package com.xjicloud.eci.instance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.lifecycle.action.LifecycleActionService;
import com.xjicloud.eci.provider.EciProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EciInstanceServiceCompensationTest {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");

    @Mock
    private EciInstanceRepository repository;
    @Mock
    private EciProvider eciProvider;
    @Mock
    private LifecycleActionService lifecycleActionService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private EciInstanceService service() {
        return new EciInstanceService(
                repository,
                new EciProperties(),
                eciProvider,
                new ObjectMapper(),
                transactionManager,
                null,
                null,
                lifecycleActionService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void startupCandidateScanIncludesCreateStopAndTaskFailedRowsAsIdsOnly() {
        UUID creatingId = UUID.randomUUID();
        UUID stoppingId = UUID.randomUUID();
        UUID failedTaskId = UUID.randomUUID();
        EciInstanceEntity creating = instance(creatingId, InstanceStatus.CREATING, null, null);
        EciInstanceEntity stopping = instance(stoppingId, InstanceStatus.STOPPING, "cloud-stop", null);
        EciInstanceEntity taskFailed = instance(failedTaskId, InstanceStatus.FAILED, "cloud-failed", UUID.randomUUID());
        EciInstanceEntity manualFailed = instance(UUID.randomUUID(), InstanceStatus.FAILED, "manual-cloud", null);
        EciInstanceEntity failedWithoutCloudId = instance(
                UUID.randomUUID(), InstanceStatus.FAILED, null, UUID.randomUUID());
        EciInstanceEntity running = instance(UUID.randomUUID(), InstanceStatus.RUNNING, "cloud-running", null);
        when(repository.findByStatusIn(anyCollection())).thenReturn(List.of(
                creating, stopping, taskFailed, manualFailed, failedWithoutCloudId, running));

        List<UUID> candidates = service().findCandidateInstanceIds();

        assertThat(candidates).containsExactly(creatingId, stoppingId, failedTaskId);
    }

    @Test
    void creatingWithoutCloudIdEnsuresQuarantinedCreateActionWithoutCallingProvider() {
        UUID id = UUID.randomUUID();
        when(repository.findForUpdateById(id)).thenReturn(Optional.of(
                instance(id, InstanceStatus.CREATING, null, null)));

        service().ensureLegacyActionsForInstance(id);

        verify(lifecycleActionService).ensureCreateAction(id);
        verify(eciProvider, never()).describe(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void creatingWithCloudIdEnsuresWaitActionWithoutCallingProvider() {
        UUID id = UUID.randomUUID();
        when(repository.findForUpdateById(id)).thenReturn(Optional.of(
                instance(id, InstanceStatus.CREATING, "legacy-cloud-id", null)));

        service().ensureLegacyActionsForInstance(id);

        verify(lifecycleActionService).ensureCreateAction(id);
        verify(eciProvider, never()).describe(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void stoppingInstanceRequestsPersistentDeleteWithoutCallingProvider() {
        UUID id = UUID.randomUUID();
        when(repository.findForUpdateById(id)).thenReturn(Optional.of(
                instance(id, InstanceStatus.STOPPING, "legacy-cloud-id", null)));

        service().ensureLegacyActionsForInstance(id);

        verify(lifecycleActionService).requestDelete(id, NOW);
        verify(eciProvider, never()).describe(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void taskLinkedFailedInstanceWithCloudIdRequestsDelete() {
        UUID id = UUID.randomUUID();
        when(repository.findForUpdateById(id)).thenReturn(Optional.of(
                instance(id, InstanceStatus.FAILED, "legacy-cloud-id", UUID.randomUUID())));

        service().ensureLegacyActionsForInstance(id);

        verify(lifecycleActionService).requestDelete(id, NOW);
    }

    @Test
    void manualFailedInstanceDoesNotCreateAutomaticDelete() {
        UUID id = UUID.randomUUID();
        when(repository.findForUpdateById(id)).thenReturn(Optional.of(
                instance(id, InstanceStatus.FAILED, "manual-cloud-id", null)));

        service().ensureLegacyActionsForInstance(id);

        verify(lifecycleActionService, never()).requestDelete(id, NOW);
        verify(lifecycleActionService, never()).ensureCreateAction(id);
    }

    private static EciInstanceEntity instance(
            UUID id, InstanceStatus status, String cloudId, UUID taskId) {
        EciInstanceEntity entity = new EciInstanceEntity();
        entity.setId(id);
        entity.setTaskId(taskId);
        entity.setName("legacy-instance");
        entity.setImage("test:image");
        entity.setCpu(1.0);
        entity.setMemory(2.0);
        entity.setStatus(status);
        entity.setCloudInstanceId(cloudId);
        entity.setCreatedAt(NOW);
        entity.setUpdatedAt(NOW);
        return entity;
    }
}
