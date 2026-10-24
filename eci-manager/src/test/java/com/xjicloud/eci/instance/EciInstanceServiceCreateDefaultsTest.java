package com.xjicloud.eci.instance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.instance.dto.CreateInstanceRequest;
import com.xjicloud.eci.lifecycle.action.LifecycleActionService;
import com.xjicloud.eci.provider.EciProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 手动创建实例时必须读取当时的默认镜像与默认共享内存，而不是构造/启动时缓存的值。
 */
@ExtendWith(MockitoExtension.class)
class EciInstanceServiceCreateDefaultsTest {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");

    @Mock
    private EciInstanceRepository repository;
    @Mock
    private EciProvider provider;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private LifecycleActionService actionService;

    private EciProperties properties;
    private EciInstanceService service;

    @BeforeEach
    void setUp() {
        properties = new EciProperties();
        service = new EciInstanceService(repository, properties, provider, new ObjectMapper(),
                transactionManager, null, null, actionService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void instanceCreationUsesCurrentDefaultImageAndShm() {
        properties.setDefaultImage("registry.example/first:1");
        properties.setDefaultShmSize("256Mi");

        service.create(new CreateInstanceRequest());

        // 配置变更后新实例必须用新默认值
        properties.setDefaultImage("registry.example/second:2");
        properties.setDefaultShmSize("512Mi");

        service.create(new CreateInstanceRequest());

        ArgumentCaptor<EciInstanceEntity> captor = ArgumentCaptor.forClass(EciInstanceEntity.class);
        verify(repository, times(2)).save(captor.capture());
        List<EciInstanceEntity> saved = captor.getAllValues();
        assertThat(saved.get(0).getImage()).isEqualTo("registry.example/first:1");
        assertThat(saved.get(0).getShmSize()).isEqualTo("256Mi");
        assertThat(saved.get(1).getImage()).isEqualTo("registry.example/second:2");
        assertThat(saved.get(1).getShmSize()).isEqualTo("512Mi");
    }
}
