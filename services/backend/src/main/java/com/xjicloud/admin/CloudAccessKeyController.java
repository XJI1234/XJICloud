package com.xjicloud.admin;

import com.xjicloud.admin.dto.CloudAccessKeyDtos.CloudAccessKeyListResponse;
import com.xjicloud.admin.dto.CloudAccessKeyDtos.CloudAccessKeyResponse;
import com.xjicloud.admin.dto.CloudAccessKeyDtos.CloudAccessKeySelectResponse;
import com.xjicloud.admin.dto.CloudAccessKeyDtos.CreateCloudAccessKeyRequest;
import com.xjicloud.admin.dto.CloudAccessKeyDtos.SelectCloudAccessKeyRequest;
import com.xjicloud.admin.dto.CloudAccessKeyDtos.TestCloudAccessKeyRequest;
import com.xjicloud.admin.dto.CloudAccessKeyDtos.TestCloudAccessKeyResponse;
import com.xjicloud.admin.dto.CloudAccessKeyDtos.UpdateCloudAccessKeyRequest;
import com.xjicloud.common.ApiResponse;
import com.xjicloud.common.BusinessException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/cloud-access-keys")
public class CloudAccessKeyController {

    private final CloudAccessKeyService cloudAccessKeyService;

    public CloudAccessKeyController(CloudAccessKeyService cloudAccessKeyService) {
        this.cloudAccessKeyService = cloudAccessKeyService;
    }

    @GetMapping
    public ApiResponse<CloudAccessKeyListResponse> list(
            @RequestParam(value = "resource", required = false) String resource
    ) {
        return ApiResponse.ok(cloudAccessKeyService.list(resource));
    }

    @PostMapping
    public ApiResponse<CloudAccessKeyResponse> create(@RequestBody CreateCloudAccessKeyRequest request) {
        return ApiResponse.ok(cloudAccessKeyService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<CloudAccessKeyResponse> get(@PathVariable("id") String id) {
        return ApiResponse.ok(cloudAccessKeyService.get(parseId(id)));
    }

    @PutMapping("/{id}")
    public ApiResponse<CloudAccessKeyResponse> update(
            @PathVariable("id") String id,
            @RequestBody UpdateCloudAccessKeyRequest request
    ) {
        return ApiResponse.ok(cloudAccessKeyService.update(parseId(id), request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") String id) {
        cloudAccessKeyService.delete(parseId(id));
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/select")
    public ApiResponse<CloudAccessKeySelectResponse> select(
            @PathVariable("id") String id,
            @RequestBody SelectCloudAccessKeyRequest request
    ) {
        return ApiResponse.ok(cloudAccessKeyService.select(parseId(id), request));
    }

    @PostMapping("/test")
    public ApiResponse<TestCloudAccessKeyResponse> testDraft(@RequestBody TestCloudAccessKeyRequest request) {
        return ApiResponse.ok(cloudAccessKeyService.testDraft(request));
    }

    @PostMapping("/{id}/test")
    public ApiResponse<TestCloudAccessKeyResponse> testSaved(
            @PathVariable("id") String id,
            @RequestBody TestCloudAccessKeyRequest request
    ) {
        return ApiResponse.ok(cloudAccessKeyService.testSaved(parseId(id), request));
    }

    private static UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("id 格式无效", HttpStatus.BAD_REQUEST);
        }
    }
}
