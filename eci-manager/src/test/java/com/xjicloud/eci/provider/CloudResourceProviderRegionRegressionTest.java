package com.xjicloud.eci.provider;

import com.aliyuncs.IAcsClient;
import com.aliyuncs.eci.model.v20180808.CreateContainerGroupRequest;
import com.aliyuncs.eci.model.v20180808.CreateContainerGroupResponse;
import com.aliyuncs.eci.model.v20180808.DeleteContainerGroupRequest;
import com.aliyuncs.eci.model.v20180808.DescribeContainerGroupsRequest;
import com.aliyuncs.eci.model.v20180808.DescribeContainerGroupsResponse;
import com.aliyuncs.eci.model.v20180808.DescribeContainerLogRequest;
import com.aliyuncs.eci.model.v20180808.DescribeContainerLogResponse;
import com.xjicloud.eci.config.EciProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CloudResourceProviderRegionRegressionTest {

    private static final String CLOUD_ID = "eci-region-regression";
    private static final String OLD_REGION = "cn-old-region";
    private static final String CURRENT_REGION = "cn-current-region";

    @Test
    void describeUsesSnapshotRegionAndFallsBackOnlyForNullOrLegacyCall() throws Exception {
        IAcsClient client = mock(IAcsClient.class);
        when(client.getAcsResponse(any(DescribeContainerGroupsRequest.class)))
                .thenReturn(containerGroupsResponse());
        EciProperties properties = properties();
        properties.setRegionId(OLD_REGION);
        EciProvider provider = new AliyunEciProvider(properties, client);
        properties.setRegionId(CURRENT_REGION);

        provider.describe(CLOUD_ID, OLD_REGION);
        provider.describe(CLOUD_ID, null);
        provider.describe(CLOUD_ID);

        ArgumentCaptor<DescribeContainerGroupsRequest> captor =
                ArgumentCaptor.forClass(DescribeContainerGroupsRequest.class);
        verify(client, times(3)).getAcsResponse(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(DescribeContainerGroupsRequest::getRegionId)
                .containsExactly(OLD_REGION, CURRENT_REGION, CURRENT_REGION);
    }

    @Test
    void deleteUsesSnapshotRegionAndFallsBackOnlyForNullOrLegacyCall() throws Exception {
        IAcsClient client = mock(IAcsClient.class);
        EciProperties properties = properties();
        properties.setRegionId(OLD_REGION);
        EciProvider provider = new AliyunEciProvider(properties, client);
        properties.setRegionId(CURRENT_REGION);

        provider.delete(CLOUD_ID, OLD_REGION);
        provider.delete(CLOUD_ID, null);
        provider.delete(CLOUD_ID);

        ArgumentCaptor<DeleteContainerGroupRequest> captor =
                ArgumentCaptor.forClass(DeleteContainerGroupRequest.class);
        verify(client, times(3)).getAcsResponse(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(DeleteContainerGroupRequest::getRegionId)
                .containsExactly(OLD_REGION, CURRENT_REGION, CURRENT_REGION);
    }

    @Test
    void fetchContainerLogUsesSnapshotRegionForLookupAndLogRequests() throws Exception {
        IAcsClient client = mock(IAcsClient.class);
        when(client.getAcsResponse(any(DescribeContainerGroupsRequest.class)))
                .thenReturn(containerGroupsResponse());
        DescribeContainerLogResponse logResponse = new DescribeContainerLogResponse();
        logResponse.setContent("log-tail");
        when(client.getAcsResponse(any(DescribeContainerLogRequest.class))).thenReturn(logResponse);
        EciProperties properties = properties();
        properties.setRegionId(OLD_REGION);
        EciProvider provider = new AliyunEciProvider(properties, client);
        properties.setRegionId(CURRENT_REGION);

        assertThat(provider.fetchContainerLog(CLOUD_ID, 40, OLD_REGION)).isEqualTo("log-tail");
        assertThat(provider.fetchContainerLog(CLOUD_ID, 40, null)).isEqualTo("log-tail");
        assertThat(provider.fetchContainerLog(CLOUD_ID, 40)).isEqualTo("log-tail");

        ArgumentCaptor<DescribeContainerGroupsRequest> lookupCaptor =
                ArgumentCaptor.forClass(DescribeContainerGroupsRequest.class);
        verify(client, times(3)).getAcsResponse(lookupCaptor.capture());
        assertThat(lookupCaptor.getAllValues())
                .extracting(DescribeContainerGroupsRequest::getRegionId)
                .containsExactly(OLD_REGION, CURRENT_REGION, CURRENT_REGION);

        ArgumentCaptor<DescribeContainerLogRequest> logCaptor =
                ArgumentCaptor.forClass(DescribeContainerLogRequest.class);
        verify(client, times(3)).getAcsResponse(logCaptor.capture());
        assertThat(logCaptor.getAllValues())
                .extracting(DescribeContainerLogRequest::getRegionId)
                .containsExactly(OLD_REGION, CURRENT_REGION, CURRENT_REGION);
        assertThat(logCaptor.getAllValues())
                .extracting(DescribeContainerLogRequest::getContainerName)
                .containsOnly("trainer");
    }

    @Test
    void createRejectsEmptySnapshotRegionWithoutCallingCloud() {
        assertEmptyNetworkValueRejected(specWithNetwork("", "vsw-submitted", "sg-submitted"));
    }

    @Test
    void createRejectsEmptySnapshotVSwitchWithoutCallingCloud() {
        assertEmptyNetworkValueRejected(specWithNetwork("cn-submitted", "", "sg-submitted"));
    }

    @Test
    void createRejectsEmptySnapshotSecurityGroupWithoutCallingCloud() {
        assertEmptyNetworkValueRejected(specWithNetwork("cn-submitted", "vsw-submitted", ""));
    }

    @Test
    void createFallsBackToCurrentNetworkValuesOnlyForNullSnapshotFields() throws Exception {
        IAcsClient client = mock(IAcsClient.class);
        CreateContainerGroupResponse response = mock(CreateContainerGroupResponse.class);
        when(response.getContainerGroupId()).thenReturn(CLOUD_ID);
        when(client.getAcsResponse(any(CreateContainerGroupRequest.class))).thenReturn(response);
        EciProperties properties = properties();
        EciProvider provider = new AliyunEciProvider(properties, client);

        provider.create(specWithNetwork(null, null, null), "local-instance-token");

        ArgumentCaptor<CreateContainerGroupRequest> captor =
                ArgumentCaptor.forClass(CreateContainerGroupRequest.class);
        verify(client).getAcsResponse(captor.capture());
        CreateContainerGroupRequest request = captor.getValue();
        assertThat(request.getRegionId()).isEqualTo(CURRENT_REGION);
        assertThat(request.getVSwitchId()).isEqualTo("vsw-current");
        assertThat(request.getSecurityGroupId()).isEqualTo("sg-current");
    }

    private static void assertEmptyNetworkValueRejected(CreateSpec spec) {
        IAcsClient client = mock(IAcsClient.class);
        EciProvider provider = new AliyunEciProvider(properties(), client);

        assertThatThrownBy(() -> provider.create(spec, "local-instance-token"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(client);
    }

    private static EciProperties properties() {
        EciProperties properties = new EciProperties();
        properties.setMock(false);
        properties.setRegionId(CURRENT_REGION);
        properties.setVswitchId("vsw-current");
        properties.setSecurityGroupId("sg-current");
        return properties;
    }

    private static DescribeContainerGroupsResponse containerGroupsResponse() {
        DescribeContainerGroupsResponse.ContainerGroup.Container container =
                new DescribeContainerGroupsResponse.ContainerGroup.Container();
        container.setName("trainer");
        DescribeContainerGroupsResponse.ContainerGroup group =
                new DescribeContainerGroupsResponse.ContainerGroup();
        group.setStatus("Running");
        group.setContainers(List.of(container));
        DescribeContainerGroupsResponse response = new DescribeContainerGroupsResponse();
        response.setContainerGroups(List.of(group));
        return response;
    }

    private static CreateSpec specWithNetwork(String regionId, String vswitchId, String securityGroupId) {
        return new CreateSpec("region-regression", "nginx:alpine", 1.0, 2.0,
                Map.of(), null, null, "Never", null, null, null, null,
                regionId, null, vswitchId, securityGroupId);
    }
}
