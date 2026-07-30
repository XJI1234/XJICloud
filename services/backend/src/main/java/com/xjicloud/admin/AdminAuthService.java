package com.xjicloud.admin;

import com.xjicloud.auth.JwtService;
import com.xjicloud.admin.dto.AdminAuthResponse;
import com.xjicloud.admin.dto.AdminLoginRequest;
import com.xjicloud.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AdminAuthService(
            AdminUserRepository adminUserRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AdminAuthResponse login(AdminLoginRequest request) {
        AdminUser admin = adminUserRepository.findByUsername(request.username().trim())
                .orElseThrow(() -> new BusinessException("用户名或密码错误", HttpStatus.UNAUTHORIZED));
        if (!passwordEncoder.matches(request.password(), admin.getPasswordHash())) {
            throw new BusinessException("用户名或密码错误", HttpStatus.UNAUTHORIZED);
        }
        String token = jwtService.generateAdminToken(admin.getId(), admin.getUsername());
        return new AdminAuthResponse(token, admin.getUsername());
    }
}
