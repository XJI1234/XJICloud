package com.xjicloud.eci.credential;

import com.xjicloud.eci.common.ApiResponse;
import com.xjicloud.eci.credential.dto.PutRuntimeCredentialRequest;
import com.xjicloud.eci.credential.dto.RuntimeCredentialsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前云钥 GET/PUT。鉴权由 {@link com.xjicloud.eci.auth.ApiKeyAuthFilter} 完成。
 */
@RestController
@RequestMapping("/api/v1/eci/runtime-credentials")
public class RuntimeCredentialController {

    private final RuntimeCredentialService service;

    public RuntimeCredentialController(RuntimeCredentialService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<RuntimeCredentialsResponse> get() {
        return ApiResponse.ok(service.snapshot());
    }

    @PutMapping
    public ApiResponse<RuntimeCredentialsResponse> put(@RequestBody(required = false) PutRuntimeCredentialRequest request) {
        return ApiResponse.ok(service.putCurrent(request));
    }
}
