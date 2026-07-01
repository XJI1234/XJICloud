package com.xjicloud.framework.auth;

import com.xjicloud.framework.common.BusinessException;
import com.xjicloud.framework.config.FrameworkProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final FrameworkUserRepository userRepository;
    private final FrameworkJwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final FrameworkProperties properties;

    public AuthService(
            FrameworkUserRepository userRepository,
            FrameworkJwtService jwtService,
            PasswordEncoder passwordEncoder,
            FrameworkProperties properties
    ) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @PostConstruct
    @Transactional
    public void initDefaultUser() {
        String username = properties.admin().defaultUsername();
        if (userRepository.findByUsername(username).isEmpty()) {
            FrameworkUser user = new FrameworkUser();
            user.setUsername(username);
            user.setPasswordHash(passwordEncoder.encode(properties.admin().defaultPassword()));
            userRepository.save(user);
        }
    }

    public AuthResponse login(String username, String password) {
        FrameworkUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户名或密码错误", HttpStatus.UNAUTHORIZED));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException("用户名或密码错误", HttpStatus.UNAUTHORIZED);
        }
        boolean mustChange = properties.admin().forcePasswordChange() && user.getPasswordChangedAt() == null;
        String token = jwtService.generateToken(user.getId(), user.getUsername());
        return new AuthResponse(token, user.getUsername(), mustChange);
    }

    @Transactional
    public void changePassword(FrameworkUser user, String oldPassword, String newPassword) {
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessException("原密码错误", HttpStatus.BAD_REQUEST);
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(java.time.Instant.now());
        userRepository.save(user);
    }

    public record AuthResponse(String token, String username, boolean mustChangePassword) {}
}
