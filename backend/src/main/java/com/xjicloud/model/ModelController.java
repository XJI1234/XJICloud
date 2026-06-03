package com.xjicloud.model;

import com.xjicloud.auth.UserAccount;
import com.xjicloud.common.ApiResponse;
import com.xjicloud.model.dto.ModelResponse;
import com.xjicloud.model.dto.SaveViewerConfigRequest;
import com.xjicloud.model.dto.ViewerConfigResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    @GetMapping("/models/{modelId}/download")
    public ResponseEntity<StreamingResponseBody> downloadModel(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable UUID modelId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader
    ) throws IOException {
        return modelService.downloadModel(user, modelId, rangeHeader);
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
