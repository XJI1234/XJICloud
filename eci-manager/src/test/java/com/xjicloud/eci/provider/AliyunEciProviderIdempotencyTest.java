package com.xjicloud.eci.provider;

import com.aliyuncs.IAcsClient;
import com.aliyuncs.eci.model.v20180808.CreateContainerGroupRequest;
import com.aliyuncs.eci.model.v20180808.CreateContainerGroupResponse;
import com.aliyuncs.eci.model.v20180808.DeleteContainerGroupRequest;
import com.aliyuncs.eci.model.v20180808.DescribeContainerGroupsRequest;
import com.aliyuncs.exceptions.ClientException;
import com.xjicloud.eci.config.EciProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AliyunEciProviderIdempotencyTest {

    @Test
    void createSendsClientTokenAndLocalInstanceTag() throws Exception {
        IAcsClient client = mock(IAcsClient.class);
        CreateContainerGroupResponse response = mock(CreateContainerGroupResponse.class);
        when(response.getContainerGroupId()).thenReturn("eci-created");
        when(client.getAcsResponse(any(CreateContainerGroupRequest.class))).thenReturn(response);
        AliyunEciProvider provider = new AliyunEciProvider(properties(), client);

        provider.create(spec(), "8b71e826-7ad1-4c60-99f4-5aaed5792689");

        ArgumentCaptor<CreateContainerGroupRequest> captor = ArgumentCaptor.forClass(CreateContainerGroupRequest.class);
        org.mockito.Mockito.verify(client).getAcsResponse(captor.capture());
        CreateContainerGroupRequest request = captor.getValue();
        assertThat(request.getClientToken()).isEqualTo("8b71e826-7ad1-4c60-99f4-5aaed5792689");
        assertThat(request.getTags()).singleElement().satisfies(tag -> {
            assertThat(tag.getKey()).isEqualTo("xjicloud-local-instance-id");
            assertThat(tag.getValue()).isEqualTo("8b71e826-7ad1-4c60-99f4-5aaed5792689");
        });
    }

    @Test
    void retryWithSameClientTokenBuildsIdenticalCreateRequestIdentity() throws Exception {
        IAcsClient client = mock(IAcsClient.class);
        CreateContainerGroupResponse response = mock(CreateContainerGroupResponse.class);
        when(response.getContainerGroupId()).thenReturn("eci-created");
        when(client.getAcsResponse(any(CreateContainerGroupRequest.class))).thenReturn(response);
        AliyunEciProvider provider = new AliyunEciProvider(properties(), client);

        String clientToken = "8b71e826-7ad1-4c60-99f4-5aaed5792689";
        provider.create(spec(), clientToken);
        provider.create(spec(), clientToken);

        ArgumentCaptor<CreateContainerGroupRequest> captor =
                ArgumentCaptor.forClass(CreateContainerGroupRequest.class);
        org.mockito.Mockito.verify(client, org.mockito.Mockito.times(2)).getAcsResponse(captor.capture());
        List<CreateContainerGroupRequest> requests = captor.getAllValues();

        CreateContainerGroupRequest first = requests.get(0);
        CreateContainerGroupRequest second = requests.get(1);
        assertThat(second.getContainerGroupName()).isEqualTo(first.getContainerGroupName());
        assertThat(second.getClientToken()).isEqualTo(first.getClientToken()).isEqualTo(clientToken);
        assertThat(second.getRegionId()).isEqualTo(first.getRegionId());
        assertThat(second.getSecurityGroupId()).isEqualTo(first.getSecurityGroupId());
        assertThat(second.getVSwitchId()).isEqualTo(first.getVSwitchId());
        assertThat(second.getTags()).usingRecursiveComparison().isEqualTo(first.getTags());
        assertThat(second.getContainers()).usingRecursiveComparison().isEqualTo(first.getContainers());
    }

    @Test
    void createClassifiesOnlyExplicitRejectionsAsRejected() throws Exception {
        assertCreateKind("InvalidParameter", CloudOperationKind.REJECTED);
        assertCreateKind("Forbidden.RAM", CloudOperationKind.REJECTED);
        assertCreateKind("QuotaExceeded", CloudOperationKind.REJECTED);
    }

    @Test
    void createClassifiesNetworkServerAndUnrecognizedErrorsAsUnknownOutcome() throws Exception {
        assertCreateKind("InvalidAction", CloudOperationKind.UNKNOWN_OUTCOME);
        assertCreateKind("ServiceUnavailable", CloudOperationKind.UNKNOWN_OUTCOME);
        assertCreateKind("SDK.TimeoutError", CloudOperationKind.UNKNOWN_OUTCOME);
    }

    @Test
    void describeAndDeleteClassifyNonNotFoundErrorsAsRetryable() throws Exception {
        IAcsClient client = mock(IAcsClient.class);
        when(client.getAcsResponse(any(DescribeContainerGroupsRequest.class)))
                .thenThrow(clientException("ServiceUnavailable", "retry later"));
        when(client.getAcsResponse(any(DeleteContainerGroupRequest.class)))
                .thenThrow(clientException("ServiceUnavailable", "retry later"));
        AliyunEciProvider provider = new AliyunEciProvider(properties(), client);

        assertThatThrownBy(() -> provider.describe("eci-id"))
                .isInstanceOf(CloudOperationException.class)
                .extracting(error -> ((CloudOperationException) error).kind())
                .isEqualTo(CloudOperationKind.RETRYABLE);
        assertThatThrownBy(() -> provider.delete("eci-id"))
                .isInstanceOf(CloudOperationException.class)
                .extracting(error -> ((CloudOperationException) error).kind())
                .isEqualTo(CloudOperationKind.RETRYABLE);
    }

    @Test
    void cloudOperationExceptionCannotExposeSensitiveSdkErrorThroughThrowableChain() throws Exception {
        IAcsClient client = mock(IAcsClient.class);
        when(client.getAcsResponse(any(CreateContainerGroupRequest.class)))
                .thenThrow(clientException("Forbidden.RAM", "AK=secret-key-material"));
        AliyunEciProvider provider = new AliyunEciProvider(properties(), client);

        Throwable thrown = catchThrowable(() -> provider.create(spec(), "local-id"));

        assertThat(thrown).isInstanceOf(CloudOperationException.class);
        for (Throwable current = thrown; current != null; current = current.getCause()) {
            assertThat(current.getMessage()).doesNotContain("secret-key-material");
        }
        assertThat(thrown.getCause()).isNull();
    }

    private static void assertCreateKind(String code, CloudOperationKind expected) throws Exception {
        IAcsClient client = mock(IAcsClient.class);
        when(client.getAcsResponse(any(CreateContainerGroupRequest.class)))
                .thenThrow(clientException(code, "opaque cloud error"));
        AliyunEciProvider provider = new AliyunEciProvider(properties(), client);

        assertThatThrownBy(() -> provider.create(spec(), "local-id"))
                .isInstanceOf(CloudOperationException.class)
                .extracting(error -> ((CloudOperationException) error).kind())
                .isEqualTo(expected);
    }

    private static ClientException clientException(String code, String message) {
        ClientException exception = new ClientException(code, message);
        exception.setRequestId("request-id");
        return exception;
    }

    private static EciProperties properties() {
        EciProperties properties = new EciProperties();
        properties.setMock(false);
        properties.setRegionId("cn-hangzhou");
        properties.setVswitchId("vsw-test");
        properties.setSecurityGroupId("sg-test");
        return properties;
    }

    private static CreateSpec spec() {
        return new CreateSpec("idempotency-test", "nginx:alpine", 1.0, 2.0,
                Map.of(), null, null, "Never", null, null, null, null);
    }
}
