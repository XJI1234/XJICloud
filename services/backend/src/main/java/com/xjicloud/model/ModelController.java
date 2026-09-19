package com.xjicloud.model;

import com.xjicloud.auth.UserAccount;
import com.xjicloud.common.ApiResponse;
import com.xjicloud.model.dto.CreateUploadSessionRequest;
import com.xjicloud.model.dto.DownloadTokenResponse;
import com.xjicloud.model.dto.ModelResponse;
import com.xjicloud.model.dto.SaveViewerConfigRequest;
import com.xjicloud.model.dto.UploadChunkResponse;
import com.xjicloud.model.dto.UploadSessionResponse;
import com.xjicloud.model.dto.ViewerConfigResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1")
public class ModelController {

    private final ModelService modelService;

    public ModelController(ModelService modelService) {
        this.modelService = modelService;
    }

    @GetMapping("/projects/{projectId}/models")
    public ApiResponse<List<ModelResponse>> listModels(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable UUID projectId
    ) {
        return ApiResponse.ok(modelService.listModels(user, projectId));
    }

    @PostMapping("/projects/{projectId}/models/upload")
    public ApiResponse<ModelResponse> uploadModel(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable UUID projectId,
            @RequestParam("file") MultipartFile file
    ) {
        return ApiResponse.ok(modelService.uploadModel(user, projectId, file));
    }

    @PostMapping("/projects/{projectId}/models/upload-sessions")
    public ApiResponse<UploadSessionResponse> createUploadSession(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateUploadSessionRequest request
    ) {
        return ApiResponse.ok(modelService.createUploadSession(user, projectId, request.fileName(), request.sizeBytes()));
    }

    @GetMapping("/models/upload-sessions/{sessionId}")
    public ApiResponse<UploadSessionResponse> getUploadSession(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable UUID sessionId
    ) {
        return ApiResponse.ok(modelService.getUploadSession(user, sessionId));
    }

    @PutMapping("/models/upload-sessions/{sessionId}/chunks")
    public ApiResponse<UploadChunkResponse> putUploadChunk(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable UUID sessionId,
            @RequestHeader("Content-Range") String contentRange,
            HttpServletRequest request
    ) throws IOException {
        return ApiResponse.ok(modelService.putUploadChunk(user, sessionId, contentRange, request.getInputStream()));
    }

    @PostMapping("/models/upload-sessions/{sessionId}/complete")
    public ApiResponse<ModelResponse> completeUploadSession(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable UUID sessionId
    ) {
        return ApiResponse.ok(modelService.completeUploadSession(user, sessionId));
    }

    @DeleteMapping("/models/upload-sessions/{sessionId}")
    public ApiResponse<Void> abortUploadSession(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable UUID sessionId
    ) {
        modelService.abortUploadSession(user, sessionId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/models/{modelId}")
    public ApiResponse<Void> deleteModel(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable UUID modelId
    ) {
        modelService.deleteModel(user, modelId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/models/{modelId}/download-token")
    public ApiResponse<DownloadTokenResponse> createDownloadToken(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable UUID modelId,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(modelService.createDownloadToken(user, modelId, request));
    }

    @GetMapping("/models/{modelId}/download")
    public ResponseEntity<StreamingResponseBody> downloadModel(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable UUID modelId,
            @RequestParam(value = "access_token", required = false) String accessToken,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader
    ) throws IOException {
        return modelService.downloadModel(user, modelId, accessToken, rangeHeader);
    }

    @GetMapping("/models/{modelId}/viewer-config")
    public ApiResponse<ViewerConfigResponse> getViewerConfig(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable UUID modelId
    ) {
        return ApiResponse.ok(modelService.getViewerConfig(user, modelId));
    }

    @PutMapping("/models/{modelId}/viewer-config")
    public ApiResponse<ViewerConfigResponse> saveViewerConfig(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable UUID modelId,
            @Valid @RequestBody SaveViewerConfigRequest request
    ) {
        return ApiResponse.ok(modelService.saveViewerConfig(user, modelId, request));
    }

    @PostMapping("/models/{modelId}/export")
    public ApiResponse<ModelResponse> exportModel(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable UUID modelId,
            @RequestParam("file") MultipartFile file
    ) {
        return ApiResponse.ok(modelService.exportModel(user, modelId, file));
    }
}
