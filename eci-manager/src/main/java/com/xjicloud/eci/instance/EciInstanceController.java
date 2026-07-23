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

@RestController
@RequestMapping("/api/v1/eci/instances")
public class EciInstanceController {

    private final EciInstanceService service;

    public EciInstanceController(EciInstanceService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<InstanceResponse> create(@RequestBody(required = false) CreateInstanceRequest request) {
        CreateInstanceRequest body = request != null ? request : new CreateInstanceRequest();
        return ApiResponse.ok(service.create(body));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<InstanceResponse> delete(@PathVariable UUID id) {
        return ApiResponse.ok(service.delete(id));
    }

    @GetMapping
    public ApiResponse<List<InstanceResponse>> list(@RequestParam(required = false) InstanceStatus status) {
        return ApiResponse.ok(service.list(status));
    }

    @GetMapping("/{id}")
    public ApiResponse<InstanceResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping("/{id}/refresh")
    public ApiResponse<InstanceResponse> refresh(@PathVariable UUID id) {
        return ApiResponse.ok(service.refresh(id));
    }
}
