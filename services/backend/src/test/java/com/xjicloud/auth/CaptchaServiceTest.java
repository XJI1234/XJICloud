package com.xjicloud.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xjicloud.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;

class CaptchaServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private CaptchaService captchaService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        captchaService = new CaptchaService(redisTemplate);
    }

    @Test
    void validateConsumesCaptchaOnce() {
        when(redisTemplate.execute(any(RedisScript.class), anyList()))
                .thenReturn("ABCD")
                .thenReturn(null);

        assertTrue(captchaService.validate("key-1", "abcd"));
        assertFalse(captchaService.validate("key-1", "abcd"));
    }

    @Test
    void validateRejectsWrongCodeAfterConsume() {
        when(redisTemplate.execute(any(RedisScript.class), anyList())).thenReturn("ABCD");

        assertFalse(captchaService.validate("key-1", "ZZZZ"));
    }

    @Test
    void generateRateLimited() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(21L);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> captchaService.generate("127.0.0.1")
        );
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
        verify(valueOps, never()).set(anyString(), anyString(), any());
    }

    @Test
    void generateWithinLimitStoresCaptcha() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(1L);

        CaptchaService.CaptchaResponse response = captchaService.generate("127.0.0.1");

        assertTrue(response.captchaKey() != null && !response.captchaKey().isBlank());
        assertTrue(response.captchaImage().startsWith("data:image/png;base64,"));
        verify(valueOps).set(anyString(), anyString(), any());
    }
}
