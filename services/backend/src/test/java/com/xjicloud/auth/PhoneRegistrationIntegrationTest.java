package com.xjicloud.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.auth.dto.SendRegistrationSmsRequest;
import com.xjicloud.auth.sms.RegistrationSmsService;
import com.xjicloud.auth.sms.SmsProperties;
import com.xjicloud.auth.sms.SmsSender;
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
        "xjicloud.sms.enabled=true",
        "xjicloud.sms.provider=aliyun-pnvs",
        "xjicloud.sms.access-key-id=test-only",
        "xjicloud.sms.access-key-secret=test-only",
        "xjicloud.sms.sign-name=test-only",
        "xjicloud.sms.template-code=test-only",
        "xjicloud.sms.code-secret=test-only-otp-hmac-secret-at-least-32-characters"
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
    private final Map<String, String> delivered = new ConcurrentHashMap<>();

    @BeforeEach
    void prepare() {
        Set<String> keys = redis.keys("xjicloud:registration-sms:*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
        users.deleteAll();
        properties.setEnabled(true);
        properties.setDailyLimit(1000);
        delivered.clear();
        doAnswer(call -> { delivered.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(sender).sendRegistrationCode(anyString(), anyString());
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
        for (int i = 0; i < 4; i++) assertThrows(BusinessException.class, () -> sms.consume("13800138002", c.challengeId(), "bad"));
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
        assertEquals(Set.of("digest", "attempts"), state.keySet());
        assertEquals(64, state.get("digest").toString().length());
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
}
