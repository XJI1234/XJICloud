package com.xjicloud.framework.node;

import com.xjicloud.framework.common.ApiResponse;
import com.xjicloud.framework.config.MasterOnly;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@MasterOnly
@RequestMapping("/api/v1/nodes")
public class NodeController {

    private final NodeService nodeService;

    public NodeController(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @GetMapping
    public ApiResponse<List<NodeService.NodeView>> list() {
        return ApiResponse.ok(nodeService.listNodes());
    }

    @PostMapping
    public ApiResponse<NodeService.NodeView> create(@RequestBody NodeService.CreateNodeRequest request) {
        return ApiResponse.ok(nodeService.createNode(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<NodeService.NodeView> update(
            @PathVariable UUID id,
            @RequestBody NodeService.UpdateNodeRequest request
    ) {
        return ApiResponse.ok(nodeService.updateNode(id, request));
    }
}
