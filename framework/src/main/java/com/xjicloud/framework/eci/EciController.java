package com.xjicloud.framework.eci;

import com.xjicloud.framework.common.ApiResponse;
import com.xjicloud.framework.config.MasterOnly;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@MasterOnly
@RequestMapping("/api/v1/eci")
public class EciController {

    private final EciInstanceService eciService;

    public EciController(EciInstanceService eciService) {
        this.eciService = eciService;
    }

    @GetMapping
    public ApiResponse<List<EciInstanceView>> list() {
        return ApiResponse.ok(eciService.listInstances());
    }

    @PostMapping
    public ApiResponse<EciInstanceView> create(@RequestBody CreateContainerRequest request) {
        return ApiResponse.ok(eciService.createInstance(request.name()));
    }

    @PostMapping("/{id}/stop")
    public ApiResponse<Void> stop(@PathVariable UUID id) {
        eciService.stopInstance(id);
        return ApiResponse.ok(null);
    }
}
