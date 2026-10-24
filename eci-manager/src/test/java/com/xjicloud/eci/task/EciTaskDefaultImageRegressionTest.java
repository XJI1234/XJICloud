package com.xjicloud.eci.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.eci.common.BusinessException;
import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.instance.EciInstanceRepository;
import com.xjicloud.eci.instance.EciInstanceService;
import com.xjicloud.eci.instance.InstanceStatus;
import com.xjicloud.eci.instance.dto.CreateInstanceRequest;
import com.xjicloud.eci.instance.dto.InstanceResponse;
import com.xjicloud.eci.task.dto.CreateTaskRequest;
import com.xjicloud.eci.task.storage.TaskStorageResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EciTaskDefaultImageRegressionTest {

    private EciTaskRepository taskRepository;
    private EciInstanceService instanceService;
    private EciProperties properties;
    private EciTaskService taskService;

    @BeforeEach
    void setUp() {
        taskRepository = mock(EciTaskRepository.class);
        instanceService = mock(EciInstanceService.class);
        properties = new EciProperties();
        taskService = new EciTaskService(
                taskRepository,
                mock(EciInstanceRepository.class),
                instanceService,
                properties,
                new ObjectMapper(),
                mock(TaskStorageResolver.class),
                mock(PlatformTransactionManager.class));
        when(instanceService.createForTask(any(), any())).thenAnswer(invocation -> {
            CreateInstanceRequest request = invocation.getArgument(0);
            UUID taskId = invocation.getArgument(1);
            Instant now = Instant.now();
            return new InstanceResponse(UUID.randomUUID(), taskId, "task", request.getImage(), 1.0, 2.0,
                    InstanceStatus.CREATING, null, null, Map.of(), null, now, now);
        });
    }

    @Test
    void explicitImageTakesPrecedenceOverCurrentDefault() {
        properties.setDefaultImage("registry.example/default:v2");
        CreateTaskRequest request = new CreateTaskRequest();
        request.setImage("registry.example/requested:v1");

        assertTaskAndInstanceUseImage(request, "registry.example/requested:v1");
    }

    @Test
    void blankRequestImageUsesCurrentDynamicDefaultForTaskAndInstance() {
        properties.setDefaultImage("registry.example/default:initial");
        properties.setDefaultImage("registry.example/default:current");
        CreateTaskRequest request = new CreateTaskRequest();
        request.setImage(" ");

        assertTaskAndInstanceUseImage(request, "registry.example/default:current");
    }

    @Test
    void rejectsWhenRequestAndCurrentDefaultImagesAreEmptyWithoutSaving() {
        properties.setDefaultImage("");
        CreateTaskRequest request = new CreateTaskRequest();
        request.setImage(" ");

        assertThatThrownBy(() -> taskService.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(taskRepository, instanceService);
    }

    private void assertTaskAndInstanceUseImage(CreateTaskRequest request, String expectedImage) {
        var response = taskService.create(request);

        ArgumentCaptor<EciTaskEntity> saved = ArgumentCaptor.forClass(EciTaskEntity.class);
        verify(taskRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(EciTaskEntity::getImage).containsOnly(expectedImage);

        ArgumentCaptor<CreateInstanceRequest> instanceRequest = ArgumentCaptor.forClass(CreateInstanceRequest.class);
        verify(instanceService).createForTask(instanceRequest.capture(), eq(response.id()));
        assertThat(instanceRequest.getValue().getImage()).isEqualTo(expectedImage);
    }
}
