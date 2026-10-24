package com.xjicloud.eci.instance;

import com.xjicloud.eci.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.lifecycle.action.LifecycleActionService;
import com.xjicloud.eci.provider.CloudInstanceView;
import com.xjicloud.eci.provider.EciProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 单元测试：refresh 在云侧 NotFound 时将 CREATING / FAILED 转为 TERMINATED。
 */
@ExtendWith(MockitoExtension.class)
class EciInstanceServiceRefreshTest {

    @Mock
    private EciInstanceRepository repository;
    @Mock
    private EciProvider eciProvider;
    @Mock
    private LifecycleActionService lifecycleActionService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private EciInstanceService service;

    @BeforeEach
    void setUp() {
        service = new EciInstanceService(
                repository,
                new EciProperties(),
                eciProvider,
                new ObjectMapper(),
                transactionManager,
                null,
                null,
                lifecycleActionService,
                Clock.fixed(Instant.parse("2026-09-21T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void refreshTransitionsCreatingToTerminatedWhenCloudMissing() {
        assertRefreshToTerminated(InstanceStatus.CREATING);
    }

    @Test
    void refreshTransitionsFailedToTerminatedWhenCloudMissing() {
        assertRefreshToTerminated(InstanceStatus.FAILED);
    }

    @Test
    void refreshTransitionsCreatingToRunningWhenCloudSucceeded() {
        UUID id = UUID.randomUUID();
        EciInstanceEntity entity = new EciInstanceEntity();
        entity.setId(id);
        entity.setName("demo");
        entity.setImage("nginx:alpine");
        entity.setCpu(1);
        entity.setMemory(2);
        entity.setStatus(InstanceStatus.CREATING);
        entity.setCloudInstanceId("eci-short");
        entity.setEnvJson("{}");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(eciProvider.describe("eci-short", null))
                .thenReturn(new CloudInstanceView("eci-short", "Succeeded", null));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.refresh(id);

        assertThat(response.status()).isEqualTo(InstanceStatus.RUNNING);
        assertThat(response.cloudStatus()).isEqualTo("Succeeded");
    }

    @Test
    void recordCloudSucceededRejectsTerminatedInstance() {
        UUID id = UUID.randomUUID();
        EciInstanceEntity entity = new EciInstanceEntity();
        entity.setId(id);
        entity.setName("demo");
        entity.setImage("nginx:alpine");
        entity.setCpu(1);
        entity.setMemory(2);
        entity.setStatus(InstanceStatus.TERMINATED);
        entity.setCloudStatus("Deleted");
        entity.setCloudInstanceId("eci-x");
        entity.setEnvJson("{}");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        when(repository.findById(id)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.recordCloudSucceeded(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许记录云侧 Succeeded");
        verify(repository, never()).save(any());
    }

    private void assertRefreshToTerminated(InstanceStatus localStatus) {
        UUID id = UUID.randomUUID();
        EciInstanceEntity entity = new EciInstanceEntity();
        entity.setId(id);
        entity.setName("demo");
        entity.setImage("nginx:alpine");
        entity.setCpu(1);
        entity.setMemory(2);
        entity.setStatus(localStatus);
        entity.setCloudInstanceId("eci-gone");
        entity.setEnvJson("{}");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        if (localStatus == InstanceStatus.FAILED) {
            entity.setFailureReason("ScheduleFailed");
        }

        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(eciProvider.describe("eci-gone", null))
                .thenReturn(new CloudInstanceView("eci-gone", "NotFound", null));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.refresh(id);

        assertThat(response.status()).isEqualTo(InstanceStatus.TERMINATED);
        assertThat(response.failureReason()).isNull();
        ArgumentCaptor<EciInstanceEntity> captor = ArgumentCaptor.forClass(EciInstanceEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(InstanceStatus.TERMINATED);
        assertThat(captor.getValue().getFailureReason()).isNull();
    }
}
