package com.xjicloud.job;

import com.xjicloud.auth.UserAccount;
import com.xjicloud.common.ApiResponse;
import com.xjicloud.job.dto.CreateDatasetRequest;
import com.xjicloud.job.dto.CreateDatasetResponse;
import com.xjicloud.job.dto.JobResponse;
import com.xjicloud.sse.JobProgressSseService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
public class JobController {

    private final TrainingJobService trainingJobService;
    private final JobProgressSseService jobProgressSseService;

    public JobController(TrainingJobService trainingJobService, JobProgressSseService jobProgressSseService) {
        this.trainingJobService = trainingJobService;
        this.jobProgressSseService = jobProgressSseService;
    }

    @PostMapping("/projects/{projectId}/datasets")
    public ApiResponse<CreateDatasetResponse> createDataset(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateDatasetRequest request
    ) {
        return ApiResponse.ok(trainingJobService.createDataset(user, projectId, request));
    }

    @PostMapping("/projects/{projectId}/datasets/{jobId}/complete")
    public ApiResponse<JobResponse> completeDataset(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable UUID projectId,
            @PathVariable UUID jobId
    ) {
        return ApiResponse.ok(trainingJobService.completeDataset(user, projectId, jobId));
    }

    @GetMapping("/projects/{projectId}/jobs")
    public ApiResponse<List<JobResponse>> listProjectJobs(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable UUID projectId
    ) {
        return ApiResponse.ok(trainingJobService.listProjectJobs(user, projectId));
    }

    @GetMapping("/jobs/{jobId}")
    public ApiResponse<JobResponse> getJob(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable UUID jobId
    ) {
        return ApiResponse.ok(trainingJobService.getJob(user, jobId));
    }

    @GetMapping(value = "/jobs/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeJobEvents(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable UUID jobId
    ) {
        trainingJobService.getJob(user, jobId);
        return jobProgressSseService.subscribe(jobId);
    }
}
