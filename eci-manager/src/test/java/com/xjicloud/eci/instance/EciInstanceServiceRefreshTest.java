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

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EciInstanceServiceRefreshTest {

    @Mock
    private EciInstanceRepository repository;
    @Mock
    private EciProvider eciProvider;
    @Mock
    private AsyncLifecycleWorker lifecycleWorker;

    private EciInstanceService service;

    @BeforeEach
    void setUp() {
        service = new EciInstanceService(
                repository,
                new EciProperties(),
                eciProvider,
                lifecycleWorker,
                new ObjectMapper());
    }

    @Test
    void refreshTransitionsCreatingToTerminatedWhenCloudMissing() {
        UUID id = UUID.randomUUID();
        EciInstanceEntity entity = new EciInstanceEntity();
        entity.setId(id);
        entity.setName("demo");
        entity.setImage("nginx:alpine");
        entity.setCpu(1);
        entity.setMemory(2);
        entity.setStatus(InstanceStatus.CREATING);
        entity.setCloudInstanceId("eci-gone");
        entity.setEnvJson("{}");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(eciProvider.describe("eci-gone"))
                .thenReturn(new CloudInstanceView("eci-gone", "NotFound", null));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.refresh(id);

        assertThat(response.status()).isEqualTo(InstanceStatus.TERMINATED);
        ArgumentCaptor<EciInstanceEntity> captor = ArgumentCaptor.forClass(EciInstanceEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(InstanceStatus.TERMINATED);
    }
}
