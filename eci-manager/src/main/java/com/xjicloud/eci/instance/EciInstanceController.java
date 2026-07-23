package com.xjicloud.eci.instance;

import com.xjicloud.eci.common.ApiResponse;
import com.xjicloud.eci.instance.dto.CreateInstanceRequest;
import com.xjicloud.eci.instance.dto.InstanceResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * ECI 实例 REST 接口，前缀 {@code /api/v1/eci/instances}。
 * <p>
 * 鉴权由 {@link com.xjicloud.eci.auth.ApiKeyAuthFilter} 完成。
 */
@RestController
@RequestMapping("/api/v1/eci/instances")
public class EciInstanceController {

    private final EciInstanceService service;

    public EciInstanceController(EciInstanceService service) {
        this.service = service;
    }

    /** 异步创建：立即返回 CREATING。 */
    @PostMapping
    public ApiResponse<InstanceResponse> create(@RequestBody(required = false) CreateInstanceRequest request) {
        CreateInstanceRequest body = request != null ? request : new CreateInstanceRequest();
        return ApiResponse.ok(service.create(body));
    }

    /** 删除云实例；已 TERMINATED 时幂等成功。 */
    @DeleteMapping("/{id}")
    public ApiResponse<InstanceResponse> delete(@PathVariable UUID id) {
        return ApiResponse.ok(service.delete(id));
    }

    /** 列表；可选按本地 status 过滤。 */
    @GetMapping
    public ApiResponse<List<InstanceResponse>> list(@RequestParam(required = false) InstanceStatus status) {
        return ApiResponse.ok(service.list(status));
    }

    /** 按本地 id 查详情。 */
    @GetMapping("/{id}")
    public ApiResponse<InstanceResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    /** 拉取云状态并回写本地记录。 */
    @PostMapping("/{id}/refresh")
    public ApiResponse<InstanceResponse> refresh(@PathVariable UUID id) {
        return ApiResponse.ok(service.refresh(id));
    }
}
