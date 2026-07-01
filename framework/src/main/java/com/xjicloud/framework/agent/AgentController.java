package com.xjicloud.framework.agent;

import com.xjicloud.framework.common.ApiResponse;
import com.xjicloud.framework.configregistry.RuntimeConfigDocument;
import com.xjicloud.framework.configregistry.RuntimeConfigService;
import com.xjicloud.framework.node.NodeRole;
import com.xjicloud.framework.node.NodeService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private final NodeService nodeService;
    private final RuntimeConfigService configService;

    public AgentController(NodeService nodeService, RuntimeConfigService configService) {
        this.nodeService = nodeService;
        this.configService = configService;
    }

    @PostMapping("/register")
    public ApiResponse<RegisterResponse> register(@RequestBody NodeService.AgentRegisterRequest request) {
        NodeService.NodeView view = nodeService.registerAgent(request);
        return ApiResponse.ok(new RegisterResponse(view.id(), configService.getSnapshot().revision()));
    }

    @PostMapping("/{nodeId}/heartbeat")
    public ApiResponse<Void> heartbeat(@PathVariable UUID nodeId, @RequestBody NodeService.AgentHeartbeatRequest request) {
        nodeService.heartbeat(nodeId, request);
        return ApiResponse.ok(null);
    }

    @GetMapping("/config")
    public ApiResponse<AgentConfigResponse> config(
            @org.springframework.web.bind.annotation.RequestParam(required = false) NodeRole role
    ) {
        if (role == null) role = NodeRole.CUSTOM;
        RuntimeConfigDocument doc = nodeService.agentConfig(role);
        return ApiResponse.ok(new AgentConfigResponse(configService.getSnapshot().revision(), doc));
    }

    public record RegisterResponse(UUID nodeId, int configRevision) {}
    public record AgentConfigResponse(int revision, RuntimeConfigDocument config) {}
}
