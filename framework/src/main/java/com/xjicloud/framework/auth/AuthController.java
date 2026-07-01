package com.xjicloud.framework.auth;

import com.xjicloud.framework.common.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthService.AuthResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request.username(), request.password()));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal FrameworkUser user,
            @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(user, request.oldPassword(), request.newPassword());
        return ApiResponse.ok("密码已更新", null);
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record ChangePasswordRequest(@NotBlank String oldPassword, @NotBlank String newPassword) {}
}
