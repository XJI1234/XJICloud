package com.xjicloud.auth;

import com.xjicloud.auth.dto.AuthResponse;
import com.xjicloud.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/fusion")
public class FusionAuthController {
    private final FusionAuthService fusion;

    public FusionAuthController(FusionAuthService fusion) { this.fusion = fusion; }

    public record LoginRequest(
            @NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}") String challengeId,
            @NotBlank @Size(max = 16384) String verifyToken) {}

    @PostMapping("/init")
    public ApiResponse<FusionAuthService.Challenge> initialize(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(fusion.initialize(request.getRemoteAddr()));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest http, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(fusion.login(request.challengeId(), request.verifyToken(), http.getRemoteAddr()));
    }
}
