package com.xjicloud.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xjicloud.auth.dto.LoginRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceFailCountTest {

    private UserAccountRepository userAccountRepository;
    private PasswordEncoder passwordEncoder;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private AuthService authService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userAccountRepository = mock(UserAccountRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        JwtService jwtService = mock(JwtService.class);
        CaptchaService captchaService = mock(CaptchaService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        authService = new AuthService(
                userAccountRepository,
                passwordEncoder,
                jwtService,
                captchaService,
                redisTemplate
        );
    }

    @Test
    void needCaptchaWhenFailCountPresent() {
        when(valueOps.get("xjicloud:login-fail:alice")).thenReturn("2");
        assertTrue(authService.needCaptcha("alice"));
        assertTrue(authService.needCaptchaResponse("alice").get("needCaptcha"));
    }

    @Test
    void needCaptchaFalseWhenNoFailCount() {
        when(valueOps.get("xjicloud:login-fail:bob")).thenReturn(null);
        assertFalse(authService.needCaptcha("bob"));
        assertFalse(authService.needCaptcha(""));
        assertFalse(authService.needCaptcha(null));
    }

    @Test
    void failedLoginIncrementsFailCountWithTtlScript() {
        when(userAccountRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(1L);

        assertThrows(
                BadCredentialsException.class,
                () -> authService.login(new LoginRequest("alice", "wrong", null, null))
        );

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(java.util.List.of("xjicloud:login-fail:alice")),
                eq("900")
        );
    }

    @Test
    void successfulLoginClearsFailCount() {
        UserAccount user = new UserAccount();
        user.setId(java.util.UUID.randomUUID());
        user.setUsername("alice");
        user.setDisplayName("Alice");
        user.setPasswordHash("hash");

        when(userAccountRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        JwtService jwtService = mock(JwtService.class);
        when(jwtService.generateToken(user)).thenReturn("token");
        when(jwtService.getExpirationMs()).thenReturn(3600L);

        AuthService service = new AuthService(
                userAccountRepository,
                passwordEncoder,
                jwtService,
                mock(CaptchaService.class),
                redisTemplate
        );

        service.login(new LoginRequest("alice", "secret", null, null));
        verify(redisTemplate).delete("xjicloud:login-fail:alice");
    }
}
