package com.xjicloud.auth;

import com.xjicloud.auth.dto.AuthResponse;
import com.xjicloud.auth.dto.LoginRequest;
import com.xjicloud.auth.dto.RegisterRequest;
import com.xjicloud.auth.dto.AccountProfile;
import com.xjicloud.auth.dto.UpdateProfileRequest;
import com.xjicloud.auth.sms.RegistrationSmsService;
import com.xjicloud.common.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String FAIL_COUNT_PREFIX = "xjicloud:login-fail:";
    private static final Duration FAIL_TTL = Duration.ofMinutes(15);

    /**
     * Atomically INCR and set TTL on first hit so the fail-count key never becomes permanent.
     */
    private static final DefaultRedisScript<Long> FAIL_INCR_SCRIPT = new DefaultRedisScript<>(
            """
            local n = redis.call('INCR', KEYS[1])
            if n == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return n
            """,
            Long.class
    );

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CaptchaService captchaService;
    private final StringRedisTemplate redisTemplate;
    private final RegistrationSmsService registrationSms;

    public AuthService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            CaptchaService captchaService,
            StringRedisTemplate redisTemplate,
            RegistrationSmsService registrationSms
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.captchaService = captchaService;
        this.redisTemplate = redisTemplate;
        this.registrationSms = registrationSms;
    }

    /** 该用户最近是否有过密码错误 → 需要验证码 */
    public boolean needCaptcha(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        String key = FAIL_COUNT_PREFIX + username.trim();
        String val = redisTemplate.opsForValue().get(key);
        try {
            return val != null && Integer.parseInt(val) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public Map<String, Boolean> needCaptchaResponse(String username) {
        return Map.of("needCaptcha", needCaptcha(username));
    }

    public void validateCaptcha(String captchaKey, String captchaCode) {
        if (!captchaService.validate(captchaKey, captchaCode)) {
            throw new BusinessException("人机验证失败，请重新输入验证码", HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException("密码的UTF-8长度不能超过72字节");
        }
        registrationSms.consume(request.phone(), request.smsChallengeId(), request.smsCode());
        if (userAccountRepository.existsByPhone(request.phone())
                || userAccountRepository.existsByUsername(request.phone())) {
            throw new BusinessException("该手机号无法注册，请尝试登录或联系管理员", HttpStatus.CONFLICT);
        }
        UserAccount user = new UserAccount();
        user.setUsername(request.phone());
        user.setPhone(request.phone());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName("用户" + request.phone().substring(7));
        try {
            userAccountRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("该手机号无法注册，请尝试登录或联系管理员", HttpStatus.CONFLICT);
        }
        return buildAuthResponse(user);
    }

    public AccountProfile profile(UserAccount principal) {
        UserAccount user = requireCurrentUser(principal);
        return new AccountProfile(user.getId(), user.getUsername(), user.getPhone(), user.getDisplayName(),
                user.getEmail(), user.getOrganization(), user.isProfileRequired());
    }

    @Transactional
    public AccountProfile updateProfile(UserAccount principal, UpdateProfileRequest request) {
        UserAccount user = userAccountRepository.findById(requireCurrentUser(principal).getId())
                .orElseThrow(() -> new BusinessException("未登录", HttpStatus.UNAUTHORIZED));
        user.setDisplayName(request.displayName().trim());
        user.setEmail(request.email().trim());
        user.setOrganization(request.organization().trim());
        if (user.getProfileCompletedAt() == null) user.setProfileCompletedAt(Instant.now());
        userAccountRepository.save(user);
        return profile(user);
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

        clearFailCount(request.username().trim());
        return buildAuthResponse(user);
    }

    private void incrementFailCount(String username) {
        String key = FAIL_COUNT_PREFIX + username;
        redisTemplate.execute(
                FAIL_INCR_SCRIPT,
                List.of(key),
                String.valueOf(FAIL_TTL.toSeconds())
        );
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
                jwtService.getUserExpirationMs(),
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.isProfileRequired()
        );
    }
}
