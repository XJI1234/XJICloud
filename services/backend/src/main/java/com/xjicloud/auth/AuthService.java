package com.xjicloud.auth;

import com.xjicloud.auth.dto.AuthResponse;
import com.xjicloud.auth.dto.LoginRequest;
import com.xjicloud.auth.dto.RegisterRequest;
import com.xjicloud.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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
        userAccountRepository.save(user);
        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        UserAccount user = userAccountRepository.findByUsername(request.username().trim())
                .orElseThrow(() -> new BadCredentialsException("用户名或密码错误"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("用户名或密码错误");
        }

        return buildAuthResponse(user);
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
