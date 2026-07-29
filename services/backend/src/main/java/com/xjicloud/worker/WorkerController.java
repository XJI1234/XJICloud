package com.xjicloud.worker;

import com.xjicloud.common.ApiResponse;
import com.xjicloud.worker.dto.JobCompleteRequest;
import com.xjicloud.worker.dto.JobFailRequest;
import com.xjicloud.worker.dto.JobProgressRequest;
import com.xjicloud.worker.dto.WorkerHeartbeatRequest;
import com.xjicloud.worker.dto.WorkerJobPayload;
import com.xjicloud.worker.dto.WorkerRegisterRequest;
import com.xjicloud.worker.dto.WorkerRegisterResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/worker")
public class WorkerController {

    private final WorkerService workerService;

    public WorkerController(WorkerService workerService) {
        this.workerService = workerService;
    }

    @PostMapping("/register")
    public ApiResponse<WorkerRegisterResponse> register(
            @RequestHeader(value = "X-Worker-Secret", required = false) String sharedSecret,
            @Valid @RequestBody WorkerRegisterRequest request
    ) {
        return ApiResponse.ok(workerService.register(sharedSecret, request));
    }

    @PostMapping("/heartbeat")
    public ApiResponse<Void> heartbeat(
            @AuthenticationPrincipal WorkerNode worker,
            @RequestBody(required = false) WorkerHeartbeatRequest request
    ) {
        workerService.heartbeat(worker, request != null ? request : new WorkerHeartbeatRequest(null));
        return ApiResponse.ok(null);
    }

    @GetMapping("/jobs/next")
    public ApiResponse<WorkerJobPayload> pollNextJob(@AuthenticationPrincipal WorkerNode worker) {
        return ApiResponse.ok(workerService.pollNextJob(worker));
    }

    @PostMapping("/jobs/{jobId}/progress")
    public ApiResponse<Void> reportProgress(
            @AuthenticationPrincipal WorkerNode worker,
            @PathVariable UUID jobId,
            @Valid @RequestBody JobProgressRequest request
    ) {
        workerService.reportProgress(worker, jobId, request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/jobs/{jobId}/complete")
    public ApiResponse<Void> completeJob(
            @AuthenticationPrincipal WorkerNode worker,
            @PathVariable UUID jobId,
            @Valid @RequestBody JobCompleteRequest request
    ) {
        workerService.completeJob(worker, jobId, request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/jobs/{jobId}/fail")
    public ApiResponse<Void> failJob(
            @AuthenticationPrincipal WorkerNode worker,
            @PathVariable UUID jobId,
            @Valid @RequestBody JobFailRequest request
    ) {
        workerService.failJob(worker, jobId, request);
        return ApiResponse.ok(null);
    }
}
