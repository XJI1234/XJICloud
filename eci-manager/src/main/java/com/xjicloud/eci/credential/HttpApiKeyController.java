package com.xjicloud.eci.credential;

import com.xjicloud.eci.common.ApiResponse;
import com.xjicloud.eci.credential.dto.HttpApiKeyStatus;
import com.xjicloud.eci.credential.dto.PutHttpApiKeyRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP Api-Key 轮换与吊销上一把。鉴权由 {@link com.xjicloud.eci.auth.ApiKeyAuthFilter} 完成。
 */
@RestController
@RequestMapping("/api/v1/eci/credentials")
public class HttpApiKeyController {

    private final HttpApiKeyStore store;

    public HttpApiKeyController(HttpApiKeyStore store) {
        this.store = store;
    }

    @PutMapping("/http-api-key")
    public ApiResponse<HttpApiKeyStatus> put(@RequestBody(required = false) PutHttpApiKeyRequest request) {
        String apiKey = request == null ? null : request.getApiKey();
        return ApiResponse.ok(store.rotate(apiKey));
    }

    @PostMapping("/revoke-previous-api-key")
    public ApiResponse<HttpApiKeyStatus> revokePrevious() {
        return ApiResponse.ok(store.revokePrevious());
    }
}
