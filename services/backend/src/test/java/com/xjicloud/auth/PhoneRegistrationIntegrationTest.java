package com.xjicloud.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.auth.dto.SendRegistrationSmsRequest;
import com.xjicloud.auth.sms.RegistrationSmsService;
import com.xjicloud.auth.sms.SmsProperties;
import com.xjicloud.auth.sms.SmsSender;
import com.xjicloud.auth.fusion.FusionAuthProvider;
import com.xjicloud.auth.fusion.FusionAuthProperties;
import com.xjicloud.common.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt-in: use a dedicated, disposable Redis on a non-production port. Never sends real SMS. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:phone-registration;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "xjicloud.storage.root=./target/phone-registration-storage",
        "xjicloud.oss.endpoint=http://127.0.0.1:19000",
        "xjicloud.oss.region=us-east-1",
        "xjicloud.oss.bucket=registration-test",
        "xjicloud.oss.access-key=test-only",
        "xjicloud.oss.secret-key=test-only",
        "xjicloud.admin.default-password=test-only-admin-password",
        "xjicloud.sms.enabled=true",
        "xjicloud.sms.provider=aliyun-pnvs",
        "xjicloud.sms.access-key-id=test-only",
        "xjicloud.sms.access-key-secret=test-only",
        "xjicloud.sms.sign-name=test-only",
        "xjicloud.sms.template-code=test-only"
        ,"xjicloud.fusion-auth.enabled=true"
        ,"xjicloud.fusion-auth.scheme-code=test-only"
        ,"xjicloud.fusion-auth.package-sign=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SMS_TEST_REDIS_PORT", matches = "[0-9]+")
class PhoneRegistrationIntegrationTest {
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        int port = Integer.parseInt(System.getenv("SMS_TEST_REDIS_PORT"));
        if (port == 6379) throw new IllegalArgumentException("Use a dedicated disposable Redis, not port 6379");
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> port);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired StringRedisTemplate redis;
    @Autowired RegistrationSmsService sms;
    @Autowired CaptchaService captcha;
    @Autowired UserAccountRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtService jwt;
    @Autowired SmsProperties properties;
    @MockBean SmsSender sender;
    @MockBean FusionAuthProvider fusionProvider;
    @Autowired FusionAuthProperties fusionProperties;
    private final Map<String, String> delivered = new ConcurrentHashMap<>();

    @BeforeEach
    void prepare() {
        Set<String> keys = redis.keys("xjicloud:registration-sms:*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
        keys = redis.keys("xjicloud:fusion-auth:*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
        fusionProperties.setEnabled(true);
        fusionProperties.setDailyLimit(1000);
        when(fusionProvider.issueAuthToken()).thenReturn("sdk-auth-token");
        when(fusionProvider.verifyPhone(anyString())).thenReturn("13800138990");
        users.deleteAll();
        properties.setEnabled(true);
        properties.setDailyLimit(1000);
        delivered.clear();
        doAnswer(call -> { delivered.put(call.getArgument(0), "012345"); return null; })
                .when(sender).sendRegistrationCode(anyString(), anyString());
        when(sender.verifyRegistrationCode(anyString(), anyString(), anyString()))
                .thenAnswer(call -> Objects.equals(delivered.get(call.getArgument(0)), call.getArgument(2)));
    }

    private String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private RegistrationSmsService.Challenge challenge(String phone) {
        String key = UUID.randomUUID().toString();
        redis.opsForValue().set("xjicloud:captcha:" + key, "ABCD", Duration.ofMinutes(5));
        return sms.send(new SendRegistrationSmsRequest(phone, key, "abcd"), "127.0.0.1");
    }

    private Map<String, String> registration(String phone, RegistrationSmsService.Challenge c) {
        return Map.of("phone", phone, "password", "Password123!", "smsChallengeId", c.challengeId(), "smsCode", delivered.get(phone));
    }

    private JsonNode register(String phone) throws Exception {
        var c = challenge(phone);
        String body = mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content(json.writeValueAsString(registration(phone, c))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("data");
    }

    @Test
    void realCaptchaSendRegisterCompleteProfileAndPasswordLogin() throws Exception {
        String captchaBody = mvc.perform(get("/api/v1/auth/captcha")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String key = json.readTree(captchaBody).path("data").path("captchaKey").asText();
        String imageCode = redis.opsForValue().get("xjicloud:captcha:" + key);
        String sendBody = mvc.perform(post("/api/v1/auth/registration-sms").contentType("application/json")
                .content(json.writeValueAsString(Map.of("phone", "13800138000", "captchaKey", key, "captchaCode", imageCode))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.smsCode").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertNull(redis.opsForValue().get("xjicloud:captcha:" + key));
        String id = json.readTree(sendBody).path("data").path("challengeId").asText();
        String authBody = mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content(json.writeValueAsString(Map.of("phone", "13800138000", "password", "Password123!",
                        "smsChallengeId", id, "smsCode", delivered.get("13800138000")))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.profileRequired").value(true))
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(authBody).path("data").path("accessToken").asText();
        mvc.perform(get("/api/v1/projects").servletPath("/api/v1/projects").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/account").header("Authorization", "Bearer " + token).contentType("application/json")
                .content(json.writeValueAsString(Map.of("displayName", "李同学", "email", "li@example.com", "organization", "XJI"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.profileRequired").value(false));
        mvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", "13800138000", "password", "Password123!"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.displayName").value("李同学"))
                .andExpect(jsonPath("$.data.profileRequired").value(false));
        assertTrue(passwords.matches("Password123!", users.findByUsername("13800138000").orElseThrow().getPasswordHash()));
    }

    @Test
    void noLegacyRegistrationBypassAndNoForeignTokensOrAccountMutation() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content("{\"username\":\"bypass\",\"password\":\"Password123!\",\"captchaKey\":\"k\",\"captchaCode\":\"ABCD\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/account")).andExpect(status().isForbidden());
        JsonNode auth = register("13800138001");
        UUID id = UUID.fromString(auth.path("userId").asText());
        for (String token : List.of(jwt.generateAdminToken(id, "admin"), jwt.generateWorkerToken(id, "worker"))) {
            mvc.perform(get("/api/v1/account").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        }
        String token = auth.path("accessToken").asText();
        mvc.perform(put("/api/v1/account").header("Authorization", "Bearer " + token).contentType("application/json")
                .content("{\"displayName\":\" \",\"email\":\"bad\",\"organization\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/account").header("Authorization", "Bearer " + token).contentType("application/json")
                .content("{\"userId\":\"00000000-0000-0000-0000-000000000000\",\"phone\":\"13800138099\",\"displayName\":\"Me\",\"email\":\"me@example.com\",\"organization\":\"Personal\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.phone").value("13800138001"))
                .andExpect(jsonPath("$.data.userId").value(id.toString()));
    }

    @Test
    void wrongPhoneFiveAttemptsExpiryAndReplayAreRejected() {
        var c = challenge("13800138002");
        assertThrows(BusinessException.class, () -> sms.consume("13800138003", c.challengeId(), delivered.get("13800138002")));
        for (int i = 0; i < 5; i++) assertThrows(BusinessException.class, () -> sms.consume("13800138002", c.challengeId(), "000000"));
        assertThrows(BusinessException.class, () -> sms.consume("13800138002", c.challengeId(), delivered.get("13800138002")));
        var expires = challenge("13800138004");
        redis.expire("xjicloud:registration-sms:challenge:" + expires.challengeId(), Duration.ZERO);
        assertThrows(BusinessException.class, () -> sms.consume("13800138004", expires.challengeId(), delivered.get("13800138004")));
        var once = challenge("13800138005");
        sms.consume("13800138005", once.challengeId(), delivered.get("13800138005"));
        assertThrows(BusinessException.class, () -> sms.consume("13800138005", once.challengeId(), delivered.get("13800138005")));
    }

    @Test
    void resendInvalidatesOldCodeAndPhoneCooldownCannotBeBypassed() throws Exception {
        var old = challenge("13800138006");
        String oldCode = delivered.get("13800138006");
        assertThrows(BusinessException.class, () -> challenge("13800138006"));
        verify(sender, times(1)).sendRegistrationCode(anyString(), anyString());
        redis.delete("xjicloud:registration-sms:cooldown:" + hash("13800138006"));
        var next = challenge("13800138006");
        assertThrows(BusinessException.class, () -> sms.consume("13800138006", old.challengeId(), oldCode));
        sms.consume("13800138006", next.challengeId(), delivered.get("13800138006"));
    }

    @Test
    void captchaDisabledProviderFailureAndGlobalLimitFailClosed() {
        assertThrows(BusinessException.class, () -> sms.send(new SendRegistrationSmsRequest("13800138007", "missing", "bad"), "ip"));
        verifyNoInteractions(sender);
        properties.setEnabled(false);
        assertThrows(BusinessException.class, () -> challenge("13800138007"));
        verifyNoInteractions(sender);
        properties.setEnabled(true);
        doThrow(new BusinessException("Provider unavailable", HttpStatus.SERVICE_UNAVAILABLE))
                .when(sender).sendRegistrationCode(anyString(), anyString());
        assertThrows(BusinessException.class, () -> challenge("13800138007"));
        assertTrue(redis.keys("xjicloud:registration-sms:challenge:*").isEmpty());
        properties.setDailyLimit(1);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS,
                assertThrows(BusinessException.class, () -> challenge("13800138008")).getStatus());
    }

    @Test
    void redisConsumptionIsAtomicUnderConcurrency() throws Exception {
        var c = challenge("13800138009");
        String code = delivered.get("13800138009");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> work = new ArrayList<>();
            for (int i = 0; i < 8; i++) work.add(() -> {
                try { sms.consume("13800138009", c.challengeId(), code); return true; }
                catch (BusinessException ex) { return false; }
            });
            int successes = 0;
            for (Future<Boolean> result : pool.invokeAll(work)) if (result.get()) successes++;
            assertEquals(1, successes);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void providerFailureNeverRegistersAndReleasesTheAttempt() {
        var c = challenge("13800138020");
        when(sender.verifyRegistrationCode(anyString(), anyString(), anyString()))
                .thenThrow(new BusinessException("Provider unavailable", HttpStatus.SERVICE_UNAVAILABLE));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(BusinessException.class,
                () -> sms.consume("13800138020", c.challengeId(), "012345")).getStatus());
        assertFalse(redis.opsForHash().hasKey("xjicloud:registration-sms:challenge:" + c.challengeId(), "verification"));
        doReturn(true).when(sender).verifyRegistrationCode(anyString(), anyString(), anyString());
        sms.consume("13800138020", c.challengeId(), "012345");
        assertThrows(BusinessException.class, () -> sms.consume("13800138020", c.challengeId(), "012345"));
        verify(sender, times(2)).verifyRegistrationCode(anyString(), anyString(), anyString());
    }

    @Test
    void resendOrExpiryDuringProviderCheckRejectsAnOtherwisePassingResult() throws Exception {
        String phone = "13800138021";
        var old = challenge(phone);
        doAnswer(call -> {
            redis.delete("xjicloud:registration-sms:cooldown:" + hash(phone));
            challenge(phone);
            return true;
        }).when(sender).verifyRegistrationCode(anyString(), anyString(), anyString());
        assertThrows(BusinessException.class, () -> sms.consume(phone, old.challengeId(), "012345"));
        var expiring = challenge("13800138022");
        doAnswer(call -> {
            redis.delete("xjicloud:registration-sms:challenge:" + expiring.challengeId());
            return true;
        }).when(sender).verifyRegistrationCode(anyString(), anyString(), anyString());
        assertThrows(BusinessException.class, () -> sms.consume("13800138022", expiring.challengeId(), "012345"));
    }

    @Test
    void concurrentSendRequestsReserveOnlyOneMessage() throws Exception {
        String phone = "13800138010";
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> work = new ArrayList<>();
            for (int i = 0; i < 8; i++) work.add(() -> {
                try { challenge(phone); return true; }
                catch (BusinessException ex) { return false; }
            });
            int successes = 0;
            for (Future<Boolean> result : pool.invokeAll(work)) if (result.get()) successes++;
            assertEquals(1, successes);
            verify(sender, times(1)).sendRegistrationCode(anyString(), anyString());
        } finally { pool.shutdownNow(); }
    }

    @Test
    void phoneAndIpQuotasUseServerStateAndStoreNoPlaintextCode() throws Exception {
        String phone = "13800138011";
        var c = challenge(phone);
        var state = redis.opsForHash().entries("xjicloud:registration-sms:challenge:" + c.challengeId());
        assertEquals(Set.of("phone", "attempts"), state.keySet());
        assertEquals(hash(phone), state.get("phone"));
        assertFalse(state.values().contains(delivered.get(phone)));
        assertTrue(redis.getExpire("xjicloud:registration-sms:challenge:" + c.challengeId()) > 0);
        redis.delete("xjicloud:registration-sms:cooldown:" + hash(phone));
        redis.opsForValue().set("xjicloud:registration-sms:phone-day:" + hash(phone), "10", Duration.ofDays(1));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, assertThrows(BusinessException.class, () -> challenge(phone)).getStatus());
        redis.opsForValue().set("xjicloud:registration-sms:ip-hour:" + hash("127.0.0.1"), "30", Duration.ofHours(1));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS,
                assertThrows(BusinessException.class, () -> challenge("13800138012")).getStatus());
        verify(sender, times(1)).sendRegistrationCode(anyString(), anyString());
    }

    @Test
    void duplicateRegistrationAndOversizeUtf8PasswordAreRejected() throws Exception {
        String phone = "13800138013";
        var c = challenge(phone);
        Map<String, String> payload = new HashMap<>(registration(phone, c));
        payload.put("password", "中".repeat(25));
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content(json.writeValueAsString(payload))).andExpect(status().isBadRequest());
        // Password validation failure must not consume a valid challenge.
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content(json.writeValueAsString(registration(phone, c)))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content(json.writeValueAsString(registration(phone, c)))).andExpect(status().isBadRequest());
        redis.delete("xjicloud:registration-sms:cooldown:" + hash(phone));
        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class, () -> challenge(phone)).getStatus());
        assertEquals(1L, users.count());
    }

    @Test
    void legacyUsernamePasswordStillWorksWithoutProfileRequirement() throws Exception {
        UserAccount legacy = new UserAccount();
        legacy.setUsername("legacy");
        legacy.setDisplayName("Legacy");
        legacy.setPasswordHash(passwords.encode("oldpass"));
        users.saveAndFlush(legacy);
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"username\":\"legacy\",\"password\":\"oldpass\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.profileRequired").value(false));
    }

    private String fusionInit() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/fusion/init"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.authToken").value("sdk-auth-token"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(900))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("challengeId").asText();
    }

    private JsonNode fusionLogin(String challenge, String token) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/fusion/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("challengeId", challenge, "verifyToken", token))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data");
    }

    @Test
    void fusionCreatesAccountWithoutSmsAndRequiresProfileThenReusesItsId() throws Exception {
        var account = fusionLogin(fusionInit(), "verified-new-account-token");
        assertTrue(account.path("profileRequired").asBoolean());
        String id = account.path("userId").asText();
        String token = account.path("accessToken").asText();
        mvc.perform(get("/api/v1/projects").servletPath("/api/v1/projects").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/account").header("Authorization", "Bearer " + token).contentType("application/json")
                .content("{\"displayName\":\"Fusion\",\"email\":\"fusion@example.com\",\"organization\":\"Lab\"}"))
                .andExpect(status().isOk());
        var again = fusionLogin(fusionInit(), "verified-existing-account-token");
        assertEquals(id, again.path("userId").asText());
        assertFalse(again.path("profileRequired").asBoolean());
        assertEquals(1, users.count());
        verifyNoInteractions(sender);
        assertFalse(passwords.matches("13800138990", users.findById(UUID.fromString(id)).orElseThrow().getPasswordHash()));
    }

    @Test
    void fusionReusesSmsUserAndPreservesPasswordAndProfile() throws Exception {
        var original = register("13800138990");
        var authenticated = fusionLogin(fusionInit(), "same-phone-token");
        assertEquals(original.path("userId").asText(), authenticated.path("userId").asText());
        assertEquals(1, users.count());
        assertTrue(passwords.matches("Password123!", users.findById(UUID.fromString(original.path("userId").asText()))
                .orElseThrow().getPasswordHash()));
    }

    @Test
    void fusionRejectsMissingExpiredOrReplayedChallengesAndTokens() throws Exception {
        String challenge = fusionInit();
        fusionLogin(challenge, "single-use-token");
        mvc.perform(post("/api/v1/auth/fusion/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("challengeId", challenge, "verifyToken", "other-token"))))
                .andExpect(status().isUnauthorized());
        String fresh = fusionInit();
        mvc.perform(post("/api/v1/auth/fusion/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("challengeId", fresh, "verifyToken", "single-use-token"))))
                .andExpect(status().isUnauthorized());
        String expired = fusionInit();
        redis.delete("xjicloud:fusion-auth:challenge:" + expired);
        mvc.perform(post("/api/v1/auth/fusion/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("challengeId", expired, "verifyToken", "expired-token"))))
                .andExpect(status().isUnauthorized());
        verify(fusionProvider, times(1)).verifyPhone(anyString());
    }

    @Test
    void fusionDoesNotBindUnverifiedLegacyUsernameOrTrustCallerPhone() throws Exception {
        UserAccount legacy = new UserAccount();
        legacy.setUsername("13800138990");
        legacy.setDisplayName("Legacy");
        legacy.setPasswordHash(passwords.encode("oldpass"));
        users.saveAndFlush(legacy);
        String challenge = fusionInit();
        mvc.perform(post("/api/v1/auth/fusion/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("challengeId", challenge, "verifyToken", "legacy-collision",
                        "phone", "13800138000"))))
                .andExpect(status().isConflict());
        assertNull(users.findByUsername("13800138990").orElseThrow().getPhone());
        assertEquals(1, users.count());
    }

    @Test
    void fusionDisabledProviderFailureAndQuotaFailClosed() throws Exception {
        fusionProperties.setEnabled(false);
        mvc.perform(post("/api/v1/auth/fusion/init")).andExpect(status().isServiceUnavailable());
        verifyNoInteractions(fusionProvider);
        fusionProperties.setEnabled(true);
        when(fusionProvider.verifyPhone(anyString())).thenThrow(new BusinessException("认证暂不可用", HttpStatus.SERVICE_UNAVAILABLE));
        String challenge = fusionInit();
        mvc.perform(post("/api/v1/auth/fusion/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("challengeId", challenge, "verifyToken", "provider-down"))))
                .andExpect(status().isServiceUnavailable());
        assertEquals(0, users.count());
        fusionProperties.setDailyLimit(1);
        mvc.perform(post("/api/v1/auth/fusion/init")).andExpect(status().isTooManyRequests());
    }

    @Test
    void concurrentFusionTokenExchangeCreatesAtMostOneUserAndConsumesOnce() throws Exception {
        String challenge = fusionInit();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Integer>> work = new ArrayList<>();
            for (int i = 0; i < 6; i++) work.add(() -> mvc.perform(post("/api/v1/auth/fusion/login")
                    .contentType("application/json").content(json.writeValueAsString(Map.of(
                            "challengeId", challenge, "verifyToken", "concurrent-same-token"))))
                    .andReturn().getResponse().getStatus());
            int successes = 0;
            for (Future<Integer> result : pool.invokeAll(work)) {
                int status = result.get();
                if (status == 200) successes++;
                else assertEquals(401, status);
            }
            assertEquals(1, successes);
            assertEquals(1, users.count());
            verify(fusionProvider, times(1)).verifyPhone("concurrent-same-token");
        } finally { pool.shutdownNow(); }
    }

    @Test
    void fusionRejectsInvalidPayloadAndDoesNotExposeTokenInRedis() throws Exception {
        mvc.perform(post("/api/v1/auth/fusion/login").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        String challenge = fusionInit();
        fusionLogin(challenge, "private-verification-token");
        Set<String> keys = redis.keys("xjicloud:fusion-auth:*");
        assertNotNull(keys);
        for (String key : keys) {
            assertFalse(key.contains("private-verification-token"));
            assertTrue(redis.getExpire(key) > 0);
            String value = redis.opsForValue().get(key);
            assertNotEquals("private-verification-token", value);
            assertNotEquals("sdk-auth-token", value);
            assertNotEquals("13800138990", value);
        }
    }
}
