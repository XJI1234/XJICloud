package com.xjicloud.eci.provider;

import com.aliyuncs.eci.model.v20180808.CreateContainerGroupRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AliyunEciProviderShmVolumeTest {

    @Test
    void attachShmVolumeMountsMemoryEmptyDirAtDevShm() {
        CreateContainerGroupRequest request = new CreateContainerGroupRequest();
        CreateContainerGroupRequest.Container container = new CreateContainerGroupRequest.Container();

        AliyunEciProvider.attachShmVolume(request, container, "8Gi");

        assertThat(request.getVolumes()).hasSize(1);
        CreateContainerGroupRequest.Volume volume =
                (CreateContainerGroupRequest.Volume) request.getVolumes().get(0);
        assertThat(volume.getName()).isEqualTo(ShmSize.VOLUME_NAME);
        assertThat(volume.getType()).isEqualTo(ShmSize.VOLUME_TYPE);
        assertThat(volume.getEmptyDirVolumeMedium()).isEqualTo(ShmSize.MEDIUM_MEMORY);
        assertThat(request.getQueryParameters())
                .containsEntry("Volume.1.EmptyDirVolume.SizeLimit", "8Gi");

        assertThat(container.getVolumeMounts()).hasSize(1);
        CreateContainerGroupRequest.Container.VolumeMount mount =
                (CreateContainerGroupRequest.Container.VolumeMount) container.getVolumeMounts().get(0);
        assertThat(mount.getName()).isEqualTo(ShmSize.VOLUME_NAME);
        assertThat(mount.getMountPath()).isEqualTo(ShmSize.MOUNT_PATH);
    }

    @Test
    void attachShmVolumeSkipsWhenBlank() {
        CreateContainerGroupRequest request = new CreateContainerGroupRequest();
        CreateContainerGroupRequest.Container container = new CreateContainerGroupRequest.Container();

        AliyunEciProvider.attachShmVolume(request, container, "  ");

        assertThat(request.getVolumes()).isNull();
        assertThat(container.getVolumeMounts()).isNull();
    }
}
