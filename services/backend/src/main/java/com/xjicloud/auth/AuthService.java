package com.xjicloud.auth;

import com.xjicloud.auth.dto.AuthResponse;
import com.xjicloud.auth.dto.LoginRequest;
import com.xjicloud.auth.dto.RegisterRequest;
import com.xjicloud.common.BusinessException;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String FAIL_COUNT_PREFIX = "xjicloud:login-fail:";
    private static final Duration FAIL_TTL = Duration.ofMinutes(15);

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CaptchaService captchaService;
    private final StringRedisTemplate redisTemplate;

    public AuthService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            CaptchaService captchaService,
            StringRedisTemplate redisTemplate
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.captchaService = captchaService;
        this.redisTemplate = redisTemplate;
    }

    /** 该用户最近是否有过密码错误 → 需要验证码 */
    public boolean needCaptcha(String username) {
        String key = FAIL_COUNT_PREFIX + username.trim();
        String val = redisTemplate.opsForValue().get(key);
        try {
            return val != null && Integer.parseInt(val) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public void validateCaptcha(String captchaKey, String captchaCode) {
        if (!captchaService.validate(captchaKey, captchaCode)) {
            throw new BusinessException("人机验证失败，请重新输入验证码", HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userAccountRepository.existsByUsername(request.username())) {
            throw new BusinessException("用户名已存在", HttpStatus.CONFLICT);
        }

        UserAccount user = new UserAccount();
        user.setUsername(request.username().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        String displayName = request.displayName() != null && !request.displayName().isBlank()
                ? request.displayName().trim()
                : request.username().trim();
        user.setDisplayName(displayName);
        user.setLoginCount(0);
        userAccountRepository.save(user);
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        UserAccount user = userAccountRepository
                .findByUsername(request.username().trim())
                .orElse(null);

        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            incrementFailCount(request.username().trim());
            throw new BadCredentialsException("用户名或密码错误");
        }

        // 密码正确 → 清除失败计数
        clearFailCount(request.username().trim());

        user.setLoginCount(user.getLoginCount() + 1);
        userAccountRepository.save(user);
        return buildAuthResponse(user);
    }

    private void incrementFailCount(String username) {
        String key = FAIL_COUNT_PREFIX + username;
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, FAIL_TTL);
    }

    private void clearFailCount(String username) {
        redisTemplate.delete(FAIL_COUNT_PREFIX + username);
    }

    public UserAccount requireCurrentUser(UserAccount principal) {
        if (principal == null) {
            throw new BusinessException("未登录", HttpStatus.UNAUTHORIZED);
        }
        return principal;
    }

    private AuthResponse buildAuthResponse(UserAccount user) {
        return new AuthResponse(
                jwtService.generateToken(user),
                "Bearer",
                jwtService.getExpirationMs(),
                user.getId(),
                user.getUsername(),
                user.getDisplayName()
        );
    }
}
