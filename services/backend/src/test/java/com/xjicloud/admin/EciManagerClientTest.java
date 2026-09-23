package com.xjicloud.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xjicloud.common.BusinessException;
import com.xjicloud.config.EciManagerProperties;
import java.time.Duration;
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
}
