package com.xjicloud.eci.config;

import com.xjicloud.eci.EciManagerApplication;
import com.xjicloud.eci.common.ApiResponse;
import com.xjicloud.eci.config.dto.CloudResourceConfigDtos.CloudResourceConfigResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 云资源配置 GET/PUT/DELETE；鉴权沿用 {@link com.xjicloud.eci.auth.ApiKeyAuthFilter}。
 * 请求体是配置键到字符串值的 Map，布尔和数字同样以文本传递。
 */
@RestController
@RequestMapping("/api/v1/eci/runtime-config")
public class CloudResourceConfigController {

    private final EciManagerApplication.CloudResourceConfigRuntime runtime;

    public CloudResourceConfigController(EciManagerApplication.CloudResourceConfigRuntime runtime) {
        this.runtime = runtime;
    }

    @GetMapping
    public ApiResponse<CloudResourceConfigResponse> get() {
        return ApiResponse.ok(CloudResourceConfigResponse.from(runtime.snapshot()));
    }

    @PutMapping("/network")
    public ApiResponse<CloudResourceConfigResponse> putNetwork(
            @RequestBody(required = false) Map<String, String> values) {
        return ApiResponse.ok(CloudResourceConfigResponse.from(runtime.saveNetwork(orEmpty(values))));
    }

    @PutMapping("/oss")
    public ApiResponse<CloudResourceConfigResponse> putOss(
            @RequestBody(required = false) Map<String, String> values) {
        return ApiResponse.ok(CloudResourceConfigResponse.from(runtime.saveOss(orEmpty(values))));
    }

    @PutMapping("/advanced")
    public ApiResponse<CloudResourceConfigResponse> putAdvanced(
            @RequestBody(required = false) Map<String, String> values) {
        return ApiResponse.ok(CloudResourceConfigResponse.from(runtime.saveAdvanced(orEmpty(values))));
    }

    @DeleteMapping("/network")
    public ApiResponse<CloudResourceConfigResponse> deleteNetwork() {
        return ApiResponse.ok(CloudResourceConfigResponse.from(runtime.resetNetwork()));
    }

    @DeleteMapping("/oss")
    public ApiResponse<CloudResourceConfigResponse> deleteOss() {
        return ApiResponse.ok(CloudResourceConfigResponse.from(runtime.resetOss()));
    }

    @DeleteMapping("/advanced")
    public ApiResponse<CloudResourceConfigResponse> deleteAdvanced() {
        return ApiResponse.ok(CloudResourceConfigResponse.from(runtime.resetAdvanced()));
    }

    private static Map<String, String> orEmpty(Map<String, String> values) {
        return values == null ? Map.of() : values;
    }
}
