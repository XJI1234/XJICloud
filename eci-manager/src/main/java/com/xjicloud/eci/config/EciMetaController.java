package com.xjicloud.eci.config;

import com.xjicloud.eci.common.ApiResponse;
import com.xjicloud.eci.config.dto.ConsoleMetaResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作台只读元数据：当前模式与默认规格。不含云密钥或 OSS endpoint/bucket。
 */
@RestController
@RequestMapping("/api/v1/eci")
public class EciMetaController {

    private final EciProperties eciProperties;
    private final OssProperties ossProperties;

    public EciMetaController(EciProperties eciProperties, OssProperties ossProperties) {
        this.eciProperties = eciProperties;
        this.ossProperties = ossProperties;
    }

    @GetMapping("/meta")
    public ApiResponse<ConsoleMetaResponse> meta() {
        String shm = eciProperties.getDefaultShmSize();
        return ApiResponse.ok(new ConsoleMetaResponse(
                eciProperties.isMock(),
                eciProperties.getRegionId(),
                eciProperties.getDefaultImage(),
                eciProperties.getDefaultCpu(),
                eciProperties.getDefaultMemory(),
                shm == null ? "" : shm,
                eciProperties.getDefaultRestartPolicy(),
                eciProperties.getDefaultMaxRuntimeSec(),
                ossProperties.isEnabled(),
                ossProperties.isConfigured(),
                eciProperties.getTaskPollIntervalSec()));
    }
}
