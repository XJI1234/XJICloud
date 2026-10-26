package com.xjicloud.auth.fusion;

import com.aliyun.dypnsapi20170525.Client;
import com.sun.net.httpserver.HttpServer;
import com.xjicloud.auth.sms.SmsProperties;
import com.xjicloud.common.BusinessException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

/** Real Alibaba SDK against loopback only; no paid provider requests. */
class AliyunFusionAuthProviderTest {
    private static final String PHONE = "13800138000";
    private static final String SUCCESS = "{\"Code\":\"OK\",\"Success\":true,\"Model\":{"
            + "\"VerifyResult\":\"PASS\",\"PhoneNumber\":\"13800138000\"}}";
    private final AtomicReference<Map<String, String>> captured = new AtomicReference<>();
    private final AtomicReference<String> action = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> response = new AtomicReference<>(SUCCESS);
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger delayMillis = new AtomicInteger();
    private HttpServer server;
    private java.util.concurrent.ExecutorService executor;
    private FusionAuthProperties properties;
    private SmsProperties sms;
    private AliyunFusionAuthProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            var parameters = new HashMap<String, String>();
            for (String pair : ((query == null ? "" : query) + "&" + body).split("&")) {
                String[] field = pair.split("=", 2);
                if (field.length == 2) parameters.put(URLDecoder.decode(field[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(field[1], StandardCharsets.UTF_8));
            }
            captured.set(parameters);
            action.set(exchange.getRequestHeaders().getFirst("x-acs-action"));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            calls.incrementAndGet();
            try {
                Thread.sleep(delayMillis.get());
                byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status.get(), bytes.length);
                try (var output = exchange.getResponseBody()) { output.write(bytes); }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        properties = configuredProperties();
        sms = sharedCredentials();
        var client = new Client(AliyunFusionAuthProvider.sharedConfig(sms)
                .setEndpoint("127.0.0.1:" + server.getAddress().getPort()).setProtocol("http"));
        provider = new AliyunFusionAuthProvider(properties, client);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void issuesAndroidTokenWithServerBoundSchemeAndPackageUsingSharedSmsRam() {
        response.set("{\"Code\":\"OK\",\"Success\":true,\"Model\":\"sdk-auth-token\"}");

        assertEquals("sdk-auth-token", provider.issueAuthToken());
        assertEquals("GetFusionAuthToken", action.get());
        assertEquals("server-scheme", captured.get().get("SchemeCode"));
        assertEquals("Android", captured.get().get("Platform"));
        assertEquals("com.xjicloud.mobile", captured.get().get("PackageName"));
        assertEquals("0123456789abcdef0123456789ABCDEF", captured.get().get("PackageSign"));
        assertEquals("900", captured.get().get("DurationSeconds"));
        assertTrue(authorization.get().contains("Credential=sms-shared-test-id"));
        assertFalse(sms.isEnabled(), "Fusion does not require SMS OTP delivery to be enabled");
        assertEquals(1, calls.get());
    }

    @Test
    void verifiesOnlyTheOpaqueTokenAndReturnsOnlyTheProviderVerifiedPhone() {
        assertEquals(PHONE, provider.verifyPhone("opaque-token+with/special=characters"));

        assertEquals("VerifyWithFusionAuthToken", action.get());
        assertEquals("opaque-token+with/special=characters", captured.get().get("VerifyToken"));
        assertFalse(captured.get().containsKey("PhoneNumber"));
        assertFalse(captured.get().containsKey("SchemeCode"));
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"Code\":\"OK\",\"Success\":true,\"Model\":{\"VerifyResult\":\"FAIL\",\"PhoneNumber\":\"13800138000\"}}",
            "{\"Code\":\"OK\",\"Success\":true,\"Model\":{\"PhoneNumber\":\"13800138000\"}}",
            "{\"Code\":\"OK\",\"Success\":true}",
            "{\"Code\":\"OK\",\"Success\":true,\"Model\":{\"VerifyResult\":\"PASS\"}}",
            "{\"Code\":\"OK\",\"Success\":true,\"Model\":{\"VerifyResult\":\"PASS\",\"PhoneNumber\":\"+8613800138000\"}}",
            "{\"Code\":\"OK\",\"Success\":true,\"Model\":{\"VerifyResult\":\"PASS\",\"PhoneNumber\":\"12800138000\"}}",
            "{\"Code\":\"OK\",\"Success\":true,\"Model\":{\"VerifyResult\":\"PASS\",\"PhoneNumber\":\"13800138000\\n\"}}"
    })
    void rejectsApparentApiSuccessWithoutSuccessfulMainlandPhoneVerification(String body) {
        response.set(body);

        assertSafeFailure(HttpStatus.UNAUTHORIZED, () -> provider.verifyPhone("sensitive-token"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"Code\":\"Denied\",\"Success\":true,\"Model\":{\"VerifyResult\":\"PASS\",\"PhoneNumber\":\"13800138000\"}}",
            "{\"Code\":\"OK\",\"Success\":false,\"Model\":{\"VerifyResult\":\"PASS\",\"PhoneNumber\":\"13800138000\"}}",
            "{\"Code\":\"OK\",\"Model\":{\"VerifyResult\":\"PASS\",\"PhoneNumber\":\"13800138000\"}}",
            "{}", "not-json"
    })
    void rejectsMalformedOrUnsuccessfulProviderResponses(String body) {
        response.set(body);

        assertSafeFailure(HttpStatus.SERVICE_UNAVAILABLE, () -> provider.verifyPhone("sensitive-token"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"Code\":\"OK\",\"Success\":true}",
            "{\"Code\":\"OK\",\"Success\":true,\"Model\":\"\"}",
            "{\"Code\":\"OK\",\"Success\":true,\"Model\":\"  \"}",
            "{\"Code\":\"Denied\",\"Success\":true,\"Model\":\"fake-token\"}",
            "{\"Code\":\"OK\",\"Success\":false,\"Model\":\"fake-token\"}"
    })
    void refusesToIssueBlankTokensOrTokensFromUnsuccessfulResponses(String body) {
        response.set(body);

        assertSafeFailure(HttpStatus.SERVICE_UNAVAILABLE, () -> provider.issueAuthToken());
    }

    @Test
    void providerHttpErrorsNeverLeakTheirMessagesAndAreNotRetried() {
        status.set(500);
        response.set("{\"Code\":\"InternalError\",\"Message\":\"sensitive-token 13800138000 sms-shared-test-secret\"}");

        assertSafeFailure(HttpStatus.SERVICE_UNAVAILABLE, () -> provider.verifyPhone("sensitive-token"));
        assertEquals(1, calls.get());
        assertSafeFailure(HttpStatus.SERVICE_UNAVAILABLE, () -> provider.issueAuthToken());
        assertEquals(2, calls.get());
    }

    @Test
    void readTimeoutFailsClosedWithoutRetrying() {
        delayMillis.set(6500);

        assertTimeoutPreemptively(Duration.ofSeconds(7), () ->
                assertSafeFailure(HttpStatus.SERVICE_UNAVAILABLE, () -> provider.verifyPhone("sensitive-token")));
        assertEquals(1, calls.get());
    }

    @Test
    void disabledFusionNeverInvokesSdk() {
        properties.setEnabled(false);

        assertSafeFailure(HttpStatus.SERVICE_UNAVAILABLE, () -> provider.issueAuthToken());
        assertSafeFailure(HttpStatus.SERVICE_UNAVAILABLE, () -> provider.verifyPhone("sensitive-token"));
        assertEquals(0, calls.get());
    }

    @Test
    void enabledFusionUsesSmsKeysEvenWhenSmsSendingIsDisabled() {
        assertDoesNotThrow(() -> new AliyunFusionAuthProvider(properties, sms));
        sms.setAccessKeySecret("");
        var error = assertThrows(IllegalStateException.class,
                () -> new AliyunFusionAuthProvider(properties, sms));
        assertFalse(error.getMessage().contains("sms-shared-test-id"));
        sms.setAccessKeySecret("sms-shared-test-secret");
        sms.setAccessKeyId("");
        assertThrows(IllegalStateException.class, () -> new AliyunFusionAuthProvider(properties, sms));
    }

    @Test
    void disabledFusionStartsWithoutSmsCredentials() {
        properties.setEnabled(false);

        assertDoesNotThrow(() -> new AliyunFusionAuthProvider(properties, new SmsProperties()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "sha256-is-not-md5", "0123456789abcdef0123456789ABCDEG"})
    void enabledFusionRejectsMissingOrInvalidAndroidSigningFingerprint(String signature) {
        properties.setPackageSign(signature);

        assertThrows(IllegalStateException.class, () -> properties.validate());
    }

    @Test
    void enabledFusionRejectsMissingServerConfiguration() {
        properties.setSchemeCode("");
        assertThrows(IllegalStateException.class, () -> properties.validate());
        properties.setSchemeCode("server-scheme");
        properties.setPackageName(" ");
        assertThrows(IllegalStateException.class, () -> properties.validate());
        properties.setPackageName("com.xjicloud.mobile");
        properties.setDailyLimit(0);
        assertThrows(IllegalStateException.class, () -> properties.validate());
    }

    @Test
    void defaultDisabledConfigurationCanStartWithoutProviderSettings() {
        var defaults = new FusionAuthProperties();

        assertFalse(defaults.isEnabled());
        assertEquals(1000, defaults.getDailyLimit());
        assertDoesNotThrow(defaults::validate);
    }

    private static void assertSafeFailure(HttpStatus expected, org.junit.jupiter.api.function.Executable action) {
        var error = assertThrows(BusinessException.class, action);
        assertEquals(expected, error.getStatus());
        assertNull(error.getCause(), "SDK causes may include credentials, tokens or phone numbers");
        assertFalse(error.getMessage().contains("sensitive-token"));
        assertFalse(error.getMessage().contains(PHONE));
        assertFalse(error.getMessage().contains("sms-shared-test-secret"));
    }

    private static FusionAuthProperties configuredProperties() {
        var p = new FusionAuthProperties();
        p.setEnabled(true);
        p.setSchemeCode("server-scheme");
        p.setPackageSign("0123456789abcdef0123456789ABCDEF");
        return p;
    }

    private static SmsProperties sharedCredentials() {
        var p = new SmsProperties();
        p.setAccessKeyId("sms-shared-test-id");
        p.setAccessKeySecret("sms-shared-test-secret");
        return p;
    }
}
