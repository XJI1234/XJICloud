package com.xjicloud.eci.provider;

import com.aliyuncs.eci.model.v20180808.DescribeContainerGroupsResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CloudStatusReasonTest {

    @Test
    void keepsAliyunDiagnosticLabelsInProviderStatusDetail() {
        DescribeContainerGroupsResponse.ContainerGroup.Container.ContainerState state =
                new DescribeContainerGroupsResponse.ContainerGroup.Container.ContainerState();
        state.setExitCode(137);
        state.setReason("InsufficientMemory");
        state.setMessage("capacity unavailable for the requested workload");

        DescribeContainerGroupsResponse.ContainerGroup.Container container =
                new DescribeContainerGroupsResponse.ContainerGroup.Container();
        container.setName("trainer");
        container.setCurrentState(state);

        DescribeContainerGroupsResponse.ContainerGroup.Event event =
                new DescribeContainerGroupsResponse.ContainerGroup.Event();
        event.setReason("FailedScheduling");
        event.setMessage("no suitable capacity is currently available");

        DescribeContainerGroupsResponse.ContainerGroup group =
                new DescribeContainerGroupsResponse.ContainerGroup();
        group.setContainers(List.of(container));
        group.setEvents(List.of(event));

        String detail = AliyunEciProvider.buildStatusDetail(group);

        assertThat(detail).isEqualTo("trainer exitCode=137 reason=InsufficientMemory "
                + "msg=capacity unavailable for the requested workload; "
                + "event=FailedScheduling:no suitable capacity is currently available");
        assertThat(CloudStatusReason.safe(detail)).isEqualTo(detail);
    }

    @Test
    void stillHidesUrlsCredentialsAndEnvironmentAssignmentsInProviderDetail() {
        List<String> sensitiveDetails = List.of(
                "reason=fetch failed https://oss.example.invalid/path",
                "msg=provider rejected accessKeyId=example-id",
                "event=worker setup failed XJI_DATASET_BUCKET=private-bucket",
                "msg=setup failed api_key=private-value",
                "msg=setup failed aws_secret_access_key=private-value",
                "msg=setup failed mixedCaseEnv=private-value",
                "failure EVENT=private-value",
                "failure REASON=private-value",
                "failure Msg=private-value",
                "failure EXITCODE=private-value");

        assertThat(sensitiveDetails.stream().map(CloudStatusReason::safe).toList())
                .containsOnly("云侧状态说明已隐藏");
    }
}
