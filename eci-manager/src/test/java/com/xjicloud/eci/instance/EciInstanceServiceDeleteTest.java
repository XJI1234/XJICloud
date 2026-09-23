package com.xjicloud.eci.instance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.lifecycle.action.LifecycleActionService;
import com.xjicloud.eci.provider.EciProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EciInstanceServiceDeleteTest {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");

    @Mock
    private EciInstanceRepository repository;
    @Mock
    private EciProvider provider;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private LifecycleActionService actionService;

    private EciInstanceService service;

    @BeforeEach
    void setUp() {
        service = new EciInstanceService(repository, new EciProperties(), provider,
                new ObjectMapper(), transactionManager, null, null, actionService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void manualDeletePersistsImmediateActionInsteadOfSchedulingWorker() {
        UUID instanceId = UUID.randomUUID();
        EciInstanceEntity instance = instance(instanceId, InstanceStatus.RUNNING, "eci-manual");
        when(repository.findForUpdateById(instanceId)).thenReturn(Optional.of(instance));

        service.delete(instanceId);

        verify(actionService).requestDelete(instanceId, NOW);
    }

    @Test
    void deletingAlreadyStoppingInstanceStillRequestsEarlierDeadline() {
        UUID instanceId = UUID.randomUUID();
        EciInstanceEntity instance = instance(instanceId, InstanceStatus.STOPPING, "eci-stopping");
        when(repository.findForUpdateById(instanceId)).thenReturn(Optional.of(instance));

        service.delete(instanceId);

        verify(actionService).requestDelete(instanceId, NOW);
    }

    @Test
    void taskCancelWithCloudlessCreatingInstancePersistsDependencyAwareDelete() {
        UUID instanceId = UUID.randomUUID();
        EciInstanceEntity instance = instance(instanceId, InstanceStatus.CREATING, null);
        when(repository.findForUpdateById(instanceId)).thenReturn(Optional.of(instance));

        service.deleteForTaskCancel(instanceId);

        verify(actionService).requestDelete(instanceId, NOW);
    }

    @Test
    void succeededTaskRecordsEvidenceAndRequestsDeleteInSameFlow() {
        UUID instanceId = UUID.randomUUID();
        EciInstanceEntity instance = instance(instanceId, InstanceStatus.RUNNING, "eci-succeeded");
        when(repository.findForUpdateById(instanceId)).thenReturn(Optional.of(instance));

        service.recordCloudSucceededAndDeleteForTask(instanceId);

        verify(actionService).requestDelete(instanceId, NOW);
    }

    private static EciInstanceEntity instance(UUID id, InstanceStatus status, String cloudId) {
        EciInstanceEntity entity = new EciInstanceEntity();
        entity.setId(id);
        entity.setName("delete-test");
        entity.setImage("nginx:alpine");
        entity.setCpu(1);
        entity.setMemory(2);
        entity.setStatus(status);
        entity.setCloudInstanceId(cloudId);
        entity.setEnvJson("{}");
        entity.setCreatedAt(NOW);
        entity.setUpdatedAt(NOW);
        return entity;
    }
}
