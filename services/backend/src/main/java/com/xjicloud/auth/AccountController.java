package com.xjicloud.auth;

import com.xjicloud.auth.dto.AccountProfile;
import com.xjicloud.auth.dto.UpdateProfileRequest;
import com.xjicloud.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/account")
public class AccountController {
    private final AuthService auth;

    public AccountController(AuthService auth) { this.auth = auth; }

    @GetMapping
    public ApiResponse<AccountProfile> get(@AuthenticationPrincipal UserAccount user) {
        return ApiResponse.ok(auth.profile(user));
    }

    @PutMapping
    public ApiResponse<AccountProfile> update(@AuthenticationPrincipal UserAccount user,
                                            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(auth.updateProfile(user, request));
    }
}
