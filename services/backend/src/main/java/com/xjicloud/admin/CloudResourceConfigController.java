package com.xjicloud.admin;

import com.xjicloud.admin.EciManagerCloudResourceConfigDtos.CloudResourceConfigView;
import com.xjicloud.common.ApiResponse;
import com.xjicloud.common.BusinessException;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员云资源配置代理：默认值、校验和持久化都在 8081。 */
@RestController
@RequestMapping("/api/v1/admin/cloud-resource-config")
public class CloudResourceConfigController {

    private static final Set<String> SECTIONS = Set.of("network", "oss", "advanced");

    private final EciManagerClient eciManagerClient;

    public CloudResourceConfigController(EciManagerClient eciManagerClient) {
        this.eciManagerClient = eciManagerClient;
    }

    @GetMapping
    public ApiResponse<CloudResourceConfigView> get() {
        return ApiResponse.ok(eciManagerClient.getCloudResourceConfig());
    }

    @PutMapping("/{section}")
    public ApiResponse<CloudResourceConfigView> save(
            @PathVariable("section") String section,
            @RequestBody(required = false) Map<String, String> values
    ) {
        requireSection(section);
        return ApiResponse.ok(eciManagerClient.saveCloudResourceConfigSection(
                section,
                values == null ? Map.of() : values
        ));
    }

    @DeleteMapping("/{section}")
    public ApiResponse<CloudResourceConfigView> reset(@PathVariable("section") String section) {
        requireSection(section);
        return ApiResponse.ok(eciManagerClient.resetCloudResourceConfigSection(section));
    }

    private static void requireSection(String section) {
        if (!SECTIONS.contains(section)) {
            throw new BusinessException("未知的配置分类：" + section, HttpStatus.BAD_REQUEST);
        }
    }
}
