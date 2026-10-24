package com.xjicloud.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xjicloud.admin.EciManagerCloudResourceConfigDtos.CloudResourceConfigField;
import com.xjicloud.admin.EciManagerCloudResourceConfigDtos.CloudResourceConfigView;
import com.xjicloud.common.BusinessException;
import com.xjicloud.config.EciManagerProperties;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

class EciManagerClientTest {

    private static final String API_KEY = "test-eci-api-key";
    private static final String FAKE_SK = "test-secret-value-not-real";

    private MockRestServiceServer server;
    private EciManagerClient client;

    @BeforeEach
    void setUp() {
        EciManagerProperties properties = new EciManagerProperties(
                "http://127.0.0.1:8081",
                API_KEY,
                Duration.ofSeconds(3),
                Duration.ofSeconds(20)
        );
        RestClient.Builder builder = RestClient.builder().baseUrl("http://127.0.0.1:8081");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new EciManagerClient(builder.build(), properties);
    }

    @AfterEach
    void verifyServer() {
        server.verify();
    }

    @Test
    void putRuntimeCredentialSendsCorrectHeadersAndBody() {
        server.expect(MockRestRequestMatchers.requestTo("http://127.0.0.1:8081/api/v1/eci/runtime-credentials"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.PUT))
                .andExpect(MockRestRequestMatchers.header("X-Api-Key", API_KEY))
                .andExpect(MockRestRequestMatchers.content().json(
                        """
                        {"resource":"ECI_OPENAPI","accessKeyId":"LTAItestkey0001","accessKeySecret":"test-secret-value-not-real"}
                        """
                ))
                .andRespond(MockRestResponseCreators.withSuccess(
                        """
                        {"success":true,"message":null,"data":{}}
                        """,
                        MediaType.APPLICATION_JSON
                ));

        client.putRuntimeCredential("ECI_OPENAPI", "LTAItestkey0001", FAKE_SK);
    }

    @Test
    void putTwiceForSelectOrder() {
        server.expect(MockRestRequestMatchers.requestTo("http://127.0.0.1:8081/api/v1/eci/runtime-credentials"))
                .andExpect(MockRestRequestMatchers.content().json(
                        """
                        {"resource":"ECI_OPENAPI","accessKeyId":"LTAItestkey0001","accessKeySecret":"test-secret-value-not-real"}
                        """
                ))
                .andRespond(MockRestResponseCreators.withSuccess(
                        """
                        {"success":true,"message":null,"data":{}}
                        """,
                        MediaType.APPLICATION_JSON
                ));
        server.expect(MockRestRequestMatchers.requestTo("http://127.0.0.1:8081/api/v1/eci/runtime-credentials"))
                .andExpect(MockRestRequestMatchers.content().json(
                        """
                        {"resource":"OSS","accessKeyId":"LTAItestkey0001","accessKeySecret":"test-secret-value-not-real"}
                        """
                ))
                .andRespond(MockRestResponseCreators.withSuccess(
                        """
                        {"success":true,"message":null,"data":{}}
                        """,
                        MediaType.APPLICATION_JSON
                ));

        client.putRuntimeCredential("ECI_OPENAPI", "LTAItestkey0001", FAKE_SK);
        client.putRuntimeCredential("OSS", "LTAItestkey0001", FAKE_SK);
    }

    @Test
    void http401MapsTo502WithApiKeyMessage() {
        server.expect(MockRestRequestMatchers.requestTo("http://127.0.0.1:8081/api/v1/eci/runtime-credentials"))
                .andRespond(MockRestResponseCreators.withUnauthorizedRequest());

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> client.putRuntimeCredential("ECI_OPENAPI", "LTAItestkey0001", FAKE_SK)
        );
        assertEquals(org.springframework.http.HttpStatus.BAD_GATEWAY, ex.getStatus());
        assertEquals("eci-manager 拒绝 X-Api-Key", ex.getMessage());
    }

    @Test
    void http400ForwardsMessage() {
        server.expect(MockRestRequestMatchers.requestTo("http://127.0.0.1:8081/api/v1/eci/runtime-credentials"))
                .andRespond(MockRestResponseCreators.withBadRequest().body(
                        """
                        {"success":false,"message":"accessKeyId 与 accessKeySecret 必须成对非空","data":null}
                        """
                ));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> client.putRuntimeCredential("OSS", "a", "b")
        );
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("accessKeyId 与 accessKeySecret 必须成对非空"));
    }

    @Test
    void http500MapsTo502WithResourceAndStatus() {
        server.expect(MockRestRequestMatchers.requestTo("http://127.0.0.1:8081/api/v1/eci/runtime-credentials"))
                .andRespond(MockRestResponseCreators.withServerError().body(
                        """
                        {"success":false,"message":"boom","data":null}
                        """
                ));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> client.putRuntimeCredential("ECI_OPENAPI", "LTAItestkey0001", FAKE_SK)
        );
        assertEquals(org.springframework.http.HttpStatus.BAD_GATEWAY, ex.getStatus());
        assertTrue(ex.getMessage().contains("resource=ECI_OPENAPI"));
        assertTrue(ex.getMessage().contains("status=500"));
    }

    @Test
    void unreachableHostReturns502() {
        EciManagerProperties properties = new EciManagerProperties(
                "http://127.0.0.1:1",
                API_KEY,
                Duration.ofMillis(200),
                Duration.ofMillis(200)
        );
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(200);
        factory.setReadTimeout(200);
        RestClient unreachableClient = RestClient.builder()
                .baseUrl("http://127.0.0.1:1")
                .requestFactory(factory)
                .build();
        EciManagerClient unreachable = new EciManagerClient(unreachableClient, properties);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> unreachable.putRuntimeCredential("OSS", "LTAItestkey0001", FAKE_SK)
        );
        assertEquals(org.springframework.http.HttpStatus.BAD_GATEWAY, ex.getStatus());
        assertEquals("eci-manager 不可达", ex.getMessage());
    }

    @Test
    void postTestOkFalseDoesNotThrow() {
        server.expect(MockRestRequestMatchers.requestTo("http://127.0.0.1:8081/api/v1/eci/runtime-credentials/test"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
                .andRespond(MockRestResponseCreators.withSuccess(
                        """
                        {"success":true,"message":null,"data":{"resource":"OSS","ok":false,"accessKeyHint":"x","detail":"denied"}}
                        """,
                        MediaType.APPLICATION_JSON
                ));

        EciManagerClient.TestResult result = client.testRuntimeCredential("OSS", "LTAItestkey0001", FAKE_SK);
        assertFalse(result.ok());
        assertEquals("denied", result.detail());
    }

    @Test
    void getTaskParsesInstanceCloudReasonCleanupAndFailureSnapshot() {
        UUID taskId = UUID.randomUUID();
        server.expect(MockRestRequestMatchers.requestTo(
                        "http://127.0.0.1:8081/api/v1/eci/tasks/" + taskId))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
                .andExpect(MockRestRequestMatchers.header("X-Api-Key", API_KEY))
                .andRespond(MockRestResponseCreators.withSuccess(
                        """
                        {"success":true,"message":null,"data":{"id":"%s","status":"FAILED","instanceStatus":"Stopped","cloudStatus":"DELETE_IN_PROGRESS","statusReason":"Instance cleanup is running","cleanupConfirmed":false,"failureReason":"Instance did not become ready"}}
                        """.formatted(taskId),
                        MediaType.APPLICATION_JSON
                ));

        EciManagerTaskResponse response = client.getTask(taskId);

        assertEquals(taskId, response.id());
        assertEquals("FAILED", response.status());
        assertEquals("Stopped", snapshotValue(response, "instanceStatus"));
        assertEquals("DELETE_IN_PROGRESS", snapshotValue(response, "cloudStatus"));
        assertEquals("Instance cleanup is running", snapshotValue(response, "statusReason"));
        assertEquals(Boolean.FALSE, snapshotValue(response, "cleanupConfirmed"));
        assertEquals("Instance did not become ready", snapshotValue(response, "failureReason"));
    }

    @Test
    void getTaskDefaultsMissingCleanupConfirmationToFalse() {
        UUID taskId = UUID.randomUUID();
        server.expect(MockRestRequestMatchers.requestTo(
                        "http://127.0.0.1:8081/api/v1/eci/tasks/" + taskId))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
                .andRespond(MockRestResponseCreators.withSuccess(
                        """
                        {"success":true,"message":null,"data":{"id":"%s","status":"STARTING"}}
                        """.formatted(taskId),
                        MediaType.APPLICATION_JSON
                ));

        EciManagerTaskResponse response = client.getTask(taskId);

        assertEquals(Boolean.FALSE, snapshotValue(response, "cleanupConfirmed"));
    }

    private Object snapshotValue(EciManagerTaskResponse response, String property) {
        Method accessor;
        try {
            accessor = EciManagerTaskResponse.class.getMethod(property);
        } catch (NoSuchMethodException ex) {
            throw new AssertionError("Task response must expose " + property, ex);
        }
        try {
            return accessor.invoke(response);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Cannot read task response property " + property, ex);
        }
    }

    @Test
    void getCloudResourceConfigSendsApiKeyAndParsesGroupedSections() {
        server.expect(MockRestRequestMatchers.requestTo("http://127.0.0.1:8081/api/v1/eci/runtime-config"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
                .andExpect(MockRestRequestMatchers.header("X-Api-Key", API_KEY))
                .andRespond(MockRestResponseCreators.withSuccess(
                        """
                        {"success":true,"message":null,"data":{"network":{"id":"network","label":"网络","fields":[{"key":"ECI_VPC_ID","displayName":"VPC","groupLabel":"","value":"vpc-test-1","source":"database","allowEmpty":true,"valueType":"TEXT","validationRule":"任意文本"}]},"oss":{"id":"oss","label":"OSS","fields":[{"key":"ECI_OSS_ENABLED","displayName":"启用 OSS","groupLabel":"","value":"true","source":"environment","allowEmpty":false,"valueType":"BOOLEAN","validationRule":"只能填写 true 或 false"}]},"advanced":{"id":"advanced","label":"高级","fields":[{"key":"ECI_DEFAULT_SHM_SIZE","displayName":"默认共享内存","groupLabel":"镜像与共享内存","value":"","source":"default","allowEmpty":true,"valueType":"SHM_SIZE","validationRule":"带单位的大小，如 256Mi 或 8Gi"}]}}}
                        """,
                        MediaType.APPLICATION_JSON
                ));

        CloudResourceConfigView view = client.getCloudResourceConfig();

        assertEquals("network", view.network().id());
        assertEquals("网络", view.network().label());
        CloudResourceConfigField vpc = view.network().fields().get(0);
        assertEquals("ECI_VPC_ID", vpc.key());
        assertEquals("VPC", vpc.displayName());
        assertEquals("vpc-test-1", vpc.value());
        assertEquals("database", vpc.source());
        assertTrue(vpc.allowEmpty());
        assertEquals("TEXT", vpc.valueType());
        assertEquals("任意文本", vpc.validationRule());

        CloudResourceConfigField ossEnabled = view.oss().fields().get(0);
        assertEquals("true", ossEnabled.value());
        assertEquals("environment", ossEnabled.source());
        assertFalse(ossEnabled.allowEmpty());
        assertEquals("BOOLEAN", ossEnabled.valueType());

        CloudResourceConfigField shmSize = view.advanced().fields().get(0);
        assertEquals("镜像与共享内存", shmSize.groupLabel());
        assertEquals("", shmSize.value());
        assertEquals("default", shmSize.source());
    }

    @Test
    void saveCloudResourceConfigSectionSendsTextValuesWithApiKey() {
        server.expect(MockRestRequestMatchers.requestTo("http://127.0.0.1:8081/api/v1/eci/runtime-config/oss"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.PUT))
                .andExpect(MockRestRequestMatchers.header("X-Api-Key", API_KEY))
                .andExpect(MockRestRequestMatchers.content().json(
                        """
                        {"ECI_OSS_ENABLED":"true","ECI_OSS_PRESIGN_EXPIRATION_MINUTES":"1440"}
                        """
                ))
                .andRespond(MockRestResponseCreators.withSuccess(
                        """
                        {"success":true,"message":null,"data":{"network":{"id":"network","label":"网络","fields":[]},"oss":{"id":"oss","label":"OSS","fields":[{"key":"ECI_OSS_ENABLED","displayName":"启用 OSS","groupLabel":"","value":"true","source":"database","allowEmpty":false,"valueType":"BOOLEAN","validationRule":"只能填写 true 或 false"},{"key":"ECI_OSS_PRESIGN_EXPIRATION_MINUTES","displayName":"预签名 URL 有效期（分钟）","groupLabel":"","value":"1440","source":"database","allowEmpty":false,"valueType":"POSITIVE_INTEGER","validationRule":"必须是大于 0 的整数"}]},"advanced":{"id":"advanced","label":"高级","fields":[]}}}
                        """,
                        MediaType.APPLICATION_JSON
                ));

        CloudResourceConfigView view = client.saveCloudResourceConfigSection(
                "oss",
                Map.of("ECI_OSS_ENABLED", "true", "ECI_OSS_PRESIGN_EXPIRATION_MINUTES", "1440")
        );

        assertEquals("oss", view.oss().id());
        assertEquals("true", view.oss().fields().get(0).value());
        assertEquals("database", view.oss().fields().get(0).source());
        assertEquals("1440", view.oss().fields().get(1).value());
        assertEquals("database", view.oss().fields().get(1).source());
    }

    @Test
    void resetCloudResourceConfigSectionUsesDeleteWithApiKey() {
        server.expect(MockRestRequestMatchers.requestTo("http://127.0.0.1:8081/api/v1/eci/runtime-config/advanced"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.DELETE))
                .andExpect(MockRestRequestMatchers.header("X-Api-Key", API_KEY))
                .andRespond(MockRestResponseCreators.withSuccess(
                        """
                        {"success":true,"message":null,"data":{"network":{"id":"network","label":"网络","fields":[]},"oss":{"id":"oss","label":"OSS","fields":[]},"advanced":{"id":"advanced","label":"高级","fields":[{"key":"ECI_CREATE_TIMEOUT_SEC","displayName":"创建超时时间（秒）","groupLabel":"创建超时","value":"1800","source":"default","allowEmpty":false,"valueType":"POSITIVE_INTEGER","validationRule":"必须是大于 0 的整数"}]}}}
                        """,
                        MediaType.APPLICATION_JSON
                ));

        CloudResourceConfigView view = client.resetCloudResourceConfigSection("advanced");

        assertEquals("advanced", view.advanced().id());
        assertEquals("ECI_CREATE_TIMEOUT_SEC", view.advanced().fields().get(0).key());
        assertEquals("1800", view.advanced().fields().get(0).value());
        assertEquals("default", view.advanced().fields().get(0).source());
    }

    @Test
    void cloudResourceConfig401MapsTo502WithApiKeyMessage() {
        server.expect(MockRestRequestMatchers.requestTo("http://127.0.0.1:8081/api/v1/eci/runtime-config"))
                .andRespond(MockRestResponseCreators.withUnauthorizedRequest());

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> client.getCloudResourceConfig()
        );
        assertEquals(org.springframework.http.HttpStatus.BAD_GATEWAY, ex.getStatus());
        assertEquals("eci-manager 拒绝 X-Api-Key", ex.getMessage());
    }

    @Test
    void cloudResourceConfig400ForwardsFieldMessage() {
        server.expect(MockRestRequestMatchers.requestTo("http://127.0.0.1:8081/api/v1/eci/runtime-config/oss"))
                .andRespond(MockRestResponseCreators.withBadRequest().body(
                        """
                        {"success":false,"message":"启用 OSS：只能填写 true 或 false","data":null}
                        """
                ));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> client.saveCloudResourceConfigSection("oss", Map.of("ECI_OSS_ENABLED", "yes"))
        );
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("启用 OSS：只能填写 true 或 false"));
    }

    @Test
    void cloudResourceConfigEmptyBodyMapsTo502() {
        server.expect(MockRestRequestMatchers.requestTo("http://127.0.0.1:8081/api/v1/eci/runtime-config"))
                .andRespond(MockRestResponseCreators.withSuccess("", MediaType.APPLICATION_JSON));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> client.getCloudResourceConfig()
        );
        assertEquals(org.springframework.http.HttpStatus.BAD_GATEWAY, ex.getStatus());
        assertEquals("eci-manager 返回空响应", ex.getMessage());
    }

    @Test
    void cloudResourceConfigUnreachableHostReturns502() {
        EciManagerProperties properties = new EciManagerProperties(
                "http://127.0.0.1:1",
                API_KEY,
                Duration.ofMillis(200),
                Duration.ofMillis(200)
        );
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(200);
        factory.setReadTimeout(200);
        RestClient unreachableClient = RestClient.builder()
                .baseUrl("http://127.0.0.1:1")
                .requestFactory(factory)
                .build();
        EciManagerClient unreachable = new EciManagerClient(unreachableClient, properties);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> unreachable.getCloudResourceConfig()
        );
        assertEquals(org.springframework.http.HttpStatus.BAD_GATEWAY, ex.getStatus());
        assertEquals("eci-manager 不可达", ex.getMessage());
    }
}
