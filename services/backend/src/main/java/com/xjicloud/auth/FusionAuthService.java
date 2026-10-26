package com.xjicloud.auth;

import com.xjicloud.auth.dto.AuthResponse;
import com.xjicloud.auth.fusion.FusionAuthProperties;
import com.xjicloud.auth.fusion.FusionAuthProvider;
import com.xjicloud.common.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Redis state contains only random challenges, token/IP hashes and quotas, never raw credentials. */
@Service
public class FusionAuthService {
    private static final String PREFIX = "xjicloud:fusion-auth:";
    private static final int EXPIRES_SECONDS = 900;
    private static final DefaultRedisScript<Long> RATE = new DefaultRedisScript<>("""
            if tonumber(redis.call('GET', KEYS[1]) or '0') >= 60 then return 0 end
            if tonumber(redis.call('GET', KEYS[2]) or '0') >= tonumber(ARGV[1]) then return 0 end
            if redis.call('INCR', KEYS[1]) == 1 then redis.call('EXPIRE', KEYS[1], 3600) end
            if redis.call('INCR', KEYS[2]) == 1 then redis.call('EXPIRE', KEYS[2], 86400) end
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
            if redis.call('SET', KEYS[2], '1', 'NX', 'EX', 86400) == false then return 0 end
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);
    private final FusionAuthProperties properties;
    private final FusionAuthProvider provider;
    private final StringRedisTemplate redis;
    private final AuthService auth;

    public FusionAuthService(FusionAuthProperties properties, FusionAuthProvider provider,
            StringRedisTemplate redis, AuthService auth) {
        this.properties = properties;
        this.provider = provider;
        this.redis = redis;
        this.auth = auth;
    }

    public record Challenge(String challengeId, String authToken, String schemeCode, String templateId, int expiresInSeconds) {}

    public Challenge initialize(String ip) {
        requireEnabled();
        rateLimit("init", ip);
        String token = provider.issueAuthToken();
        if (token == null || token.isBlank()) throw unavailable();
        String challenge = UUID.randomUUID().toString();
        redis.opsForValue().set(PREFIX + "challenge:" + challenge, "1", Duration.ofSeconds(EXPIRES_SECONDS));
        return new Challenge(challenge, token, properties.getSchemeCode(), properties.getTemplateId(), EXPIRES_SECONDS);
    }

    public AuthResponse login(String challengeId, String verifyToken, String ip) {
        requireEnabled();
        rateLimit("login", ip);
        Long consumed = redis.execute(CONSUME, List.of(PREFIX + "challenge:" + challengeId,
                PREFIX + "used:" + hash(verifyToken)));
        if (!Long.valueOf(1).equals(consumed)) {
            throw new BusinessException("认证已过期或已使用，请重新认证", HttpStatus.UNAUTHORIZED);
        }
        // Consume before verification: a timeout must never turn into an automatic retry/login.
        return auth.loginWithVerifiedPhone(provider.verifyPhone(verifyToken));
    }

    private void rateLimit(String action, String ip) {
        Long allowed = redis.execute(RATE, List.of(PREFIX + action + ":ip-hour:" + hash(ip),
                PREFIX + action + ":day"), Integer.toString(properties.getDailyLimit()));
        if (!Long.valueOf(1).equals(allowed)) {
            throw new BusinessException("本机号码认证操作过于频繁，请稍后重试", HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) throw unavailable();
    }

    private static BusinessException unavailable() {
        return new BusinessException("本机号码认证暂不可用，请使用短信验证或密码登录", HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }
}
