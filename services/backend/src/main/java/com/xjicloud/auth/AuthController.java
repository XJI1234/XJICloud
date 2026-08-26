package com.xjicloud.auth;

import com.xjicloud.auth.CaptchaService.CaptchaResponse;
import com.xjicloud.auth.dto.AuthResponse;
import com.xjicloud.auth.dto.LoginRequest;
import com.xjicloud.auth.dto.RegisterRequest;
import com.xjicloud.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;

    public AuthController(AuthService authService, CaptchaService captchaService) {
        this.authService = authService;
        this.captchaService = captchaService;
    }

    @GetMapping("/captcha")
    public ApiResponse<CaptchaResponse> captcha(HttpServletRequest request) {
        return ApiResponse.ok(captchaService.generate(resolveClientIp(request)));
    }

    @GetMapping("/need-captcha")
    public ApiResponse<Map<String, Boolean>> needCaptcha(@RequestParam String username) {
        return ApiResponse.ok(authService.needCaptchaResponse(username));
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        authService.validateCaptcha(request.captchaKey(), request.captchaCode());
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        if (authService.needCaptcha(request.username())) {
            authService.validateCaptcha(request.captchaKey(), request.captchaCode());
        }
        return ApiResponse.ok(authService.login(request));
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
