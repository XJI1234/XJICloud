package com.xjicloud.eci.task;

import com.xjicloud.eci.common.ApiResponse;
import com.xjicloud.eci.task.dto.CreateTaskRequest;
import com.xjicloud.eci.task.dto.TaskResponse;
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
 * 任务 REST 接口，前缀 {@code /api/v1/eci/tasks}。
 * <p>
 * 鉴权由 {@link com.xjicloud.eci.auth.ApiKeyAuthFilter} 完成。
 */
@RestController
@RequestMapping("/api/v1/eci/tasks")
public class EciTaskController {

    private final EciTaskService service;

    public EciTaskController(EciTaskService service) {
        this.service = service;
    }

    /** 提交任务：立即返回 taskId + STARTING。 */
    @PostMapping
    public ApiResponse<TaskResponse> create(@RequestBody(required = false) CreateTaskRequest request) {
        return ApiResponse.ok(service.create(request != null ? request : new CreateTaskRequest()));
    }

    /** 列表；可选按 status 过滤。 */
    @GetMapping
    public ApiResponse<List<TaskResponse>> list(@RequestParam(required = false) TaskStatus status) {
        return ApiResponse.ok(service.list(status));
    }

    /** 按本地 id 查详情。 */
    @GetMapping("/{id}")
    public ApiResponse<TaskResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    /** 取消任务；已 CANCELLED 时幂等成功。 */
    @PostMapping("/{id}/cancel")
    public ApiResponse<TaskResponse> cancel(@PathVariable UUID id) {
        return ApiResponse.ok(service.cancel(id));
    }
}
