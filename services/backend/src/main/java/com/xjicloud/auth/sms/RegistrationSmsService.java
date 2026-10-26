package com.xjicloud.auth.sms;

import com.xjicloud.auth.CaptchaService;
import com.xjicloud.auth.UserAccountRepository;
import com.xjicloud.auth.dto.SendRegistrationSmsRequest;
import com.xjicloud.common.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class RegistrationSmsService {
    public static final int EXPIRES_SECONDS = 300;
    public static final int RETRY_SECONDS = 60;
    private static final String PREFIX = "xjicloud:registration-sms:";
    private static final DefaultRedisScript<Long> RESERVE = script("reserve");
    private static final DefaultRedisScript<Long> ACTIVATE = script("activate");
    private static final DefaultRedisScript<Long> BEGIN_VERIFY = script("begin-verify");
    private static final DefaultRedisScript<Long> FINISH_VERIFY = script("finish-verify");
    private final StringRedisTemplate redis;
    private final SmsSender sender;
    private final SmsProperties properties;
    private final CaptchaService captcha;
    private final UserAccountRepository users;

    public RegistrationSmsService(StringRedisTemplate redis, SmsSender sender, SmsProperties properties,
                                  CaptchaService captcha, UserAccountRepository users) {
        this.redis = redis;
        this.sender = sender;
        this.properties = properties;
        this.captcha = captcha;
        this.users = users;
    }

    public record Challenge(String challengeId, int expiresInSeconds, int retryAfterSeconds) {}

    public Challenge send(SendRegistrationSmsRequest request, String remoteAddress) {
        if (!properties.isEnabled()) {
            throw new BusinessException("短信注册尚未开通，请联系管理员", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (!captcha.validate(request.captchaKey(), request.captchaCode())) {
            throw new BusinessException("图形验证码错误或已过期，请刷新后重试");
        }
        String phone = request.phone();
        String phoneHash = hash(phone);
        String ipHash = hash(remoteAddress);
        Long reserved = execute(RESERVE, List.of(PREFIX + "cooldown:" + phoneHash,
                PREFIX + "phone-day:" + phoneHash, PREFIX + "ip-hour:" + ipHash, PREFIX + "global-day"),
                Integer.toString(properties.getDailyLimit()));
        if (!Long.valueOf(1).equals(reserved)) {
            throw new BusinessException("短信发送过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS);
        }
        if (users.existsByPhone(phone) || users.existsByUsername(phone)) {
            throw new BusinessException("该手机号无法注册，请尝试登录或联系管理员", HttpStatus.CONFLICT);
        }
        String id = UUID.randomUUID().toString();
        sender.sendRegistrationCode(phone, id);
        Long activated = execute(ACTIVATE, List.of(PREFIX + "active:" + phoneHash, PREFIX + "challenge:" + id),
                id, phoneHash, Integer.toString(EXPIRES_SECONDS), PREFIX + "challenge:");
        if (!Long.valueOf(1).equals(activated)) {
            throw new BusinessException("短信验证暂不可用，请稍后重新获取", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return new Challenge(id, EXPIRES_SECONDS, RETRY_SECONDS);
    }

    public void consume(String phone, String id, String code) {
        if (!properties.isEnabled()) {
            throw new BusinessException("短信注册尚未开通，请联系管理员", HttpStatus.SERVICE_UNAVAILABLE);
        }
        String phoneHash = hash(phone);
        String token = UUID.randomUUID().toString();
        List<String> keys = List.of(PREFIX + "challenge:" + id, PREFIX + "active:" + phoneHash);
        Long acquired = execute(BEGIN_VERIFY, keys, phoneHash, id, token);
        if (!Long.valueOf(1).equals(acquired)) throw invalidCode();
        boolean passed;
        try {
            passed = sender.verifyRegistrationCode(phone, id, code);
        } catch (RuntimeException ex) {
            // Release this attempt, but never authorize after an ambiguous provider/network failure.
            execute(FINISH_VERIFY, keys, token, "0", id);
            throw ex;
        }
        Long consumed = execute(FINISH_VERIFY, keys, token, passed ? "1" : "0", id);
        if (!passed || !Long.valueOf(1).equals(consumed)) throw invalidCode();
    }

    private static BusinessException invalidCode() {
        return new BusinessException("短信验证码错误、已过期或尝试次数过多，请重新获取");
    }

    private Long execute(DefaultRedisScript<Long> script, List<String> keys, String... arguments) {
        try {
            return redis.execute(script, keys, (Object[]) arguments);
        } catch (DataAccessException ex) {
            throw new BusinessException("短信验证暂不可用，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    private static DefaultRedisScript<Long> script(String name) {
        var script = new DefaultRedisScript<Long>();
        script.setLocation(new ClassPathResource("sms/" + name + ".lua"));
        script.setResultType(Long.class);
        return script;
    }
}
