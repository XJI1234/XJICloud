package com.xjicloud.framework.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.framework.common.BusinessException;
import com.xjicloud.framework.configregistry.RuntimeConfigDocument;
import com.xjicloud.framework.configregistry.RuntimeConfigService;
import com.xjicloud.framework.monitor.SystemMetricsService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NodeService {

    private final ManagedNodeRepository repository;
    private final RuntimeConfigService configService;
    private final SystemMetricsService metricsService;
    private final ObjectMapper objectMapper;

    public NodeService(
            ManagedNodeRepository repository,
            RuntimeConfigService configService,
            SystemMetricsService metricsService,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.configService = configService;
        this.metricsService = metricsService;
        this.objectMapper = objectMapper;
    }

    public List<NodeView> listNodes() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toView).toList();
    }

    @Transactional
    public NodeView createNode(CreateNodeRequest request) {
        ManagedNode node = new ManagedNode();
        node.setName(request.name());
        node.setHost(request.host());
        node.setSshPort(request.sshPort() != null ? request.sshPort() : 22);
        node.setSshUser(request.sshUser() != null ? request.sshUser() : "root");
        node.setRole(request.role() != null ? request.role() : NodeRole.CUSTOM);
        node.setDeployCapabilitiesJson(defaultCapabilities());
        node.setDeployProfileJson(defaultProfile(request.role()));
        repository.save(node);
        return toView(node);
    }

    @Transactional
    public NodeView updateNode(UUID id, UpdateNodeRequest request) {
        ManagedNode node = repository.findById(id)
                .orElseThrow(() -> new BusinessException("节点不存在", HttpStatus.NOT_FOUND));
        if (request.name() != null) node.setName(request.name());
        if (request.host() != null) node.setHost(request.host());
        if (request.sshPort() != null) node.setSshPort(request.sshPort());
        if (request.sshUser() != null) node.setSshUser(request.sshUser());
        if (request.role() != null) node.setRole(request.role());
        repository.save(node);
        return toView(node);
    }

    @Transactional
    public NodeView registerAgent(AgentRegisterRequest request) {
        ManagedNode node = repository.findByHost(request.host()).orElseGet(() -> {
            ManagedNode n = new ManagedNode();
            n.setName(request.name() != null ? request.name() : request.host());
            n.setHost(request.host());
            n.setRole(request.role() != null ? request.role() : NodeRole.CUSTOM);
            n.setDeployCapabilitiesJson(defaultCapabilities());
            n.setDeployProfileJson(defaultProfile(request.role()));
            return n;
        });
        node.setAgentStatus(AgentStatus.ONLINE);
        node.setLastHeartbeat(Instant.now());
        if (request.systemInfoJson() != null) {
            node.setSystemInfoJson(request.systemInfoJson());
        } else {
            node.setSystemInfoJson(metricsService.collectMetricsJson());
        }
        if (request.role() != null) node.setRole(request.role());
        repository.save(node);
        return toView(node);
    }

    @Transactional
    public void heartbeat(UUID nodeId, AgentHeartbeatRequest request) {
        ManagedNode node = repository.findById(nodeId)
                .orElseThrow(() -> new BusinessException("节点不存在", HttpStatus.NOT_FOUND));
        node.setAgentStatus(AgentStatus.ONLINE);
        node.setLastHeartbeat(Instant.now());
        if (request.systemInfoJson() != null) {
            node.setSystemInfoJson(request.systemInfoJson());
        }
        if (request.configRevision() != null) {
            node.setLastConfigRevision(request.configRevision());
        }
        repository.save(node);
    }

    public ManagedNode getNode(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException("节点不存在", HttpStatus.NOT_FOUND));
    }

    public RuntimeConfigDocument agentConfig(NodeRole role) {
        return configService.getSubsetForRole(role != null ? role.name() : null);
    }

    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void markStaleOffline() {
        Instant threshold = Instant.now().minus(60, ChronoUnit.SECONDS);
        for (ManagedNode node : repository.findAll()) {
            if (node.getLastHeartbeat() != null && node.getLastHeartbeat().isBefore(threshold)) {
                node.setAgentStatus(AgentStatus.OFFLINE);
                repository.save(node);
            }
        }
    }

    private NodeView toView(ManagedNode node) {
        return new NodeView(
                node.getId(), node.getName(), node.getHost(), node.getSshPort(), node.getSshUser(),
                node.getRole(), node.getAgentStatus(), node.getLastHeartbeat(), node.getLastConfigRevision(),
                node.getSystemInfoJson(), node.getCreatedAt()
        );
    }

    private String defaultCapabilities() {
        return "{\"basic\":true,\"docker\":true,\"k8s\":false}";
    }

    private String defaultProfile(NodeRole role) {
        if (role == NodeRole.FRONTEND) {
            return "{\"sourcePath\":\"/opt/xjicloud\",\"buildCmd\":\"npm ci && npm run build:all:cloud\"}";
        }
        if (role == NodeRole.BACKEND) {
            return "{\"sourcePath\":\"/opt/xjicloud/backend\",\"buildCmd\":\"mvn -DskipTests package\"}";
        }
        return "{}";
    }

    public record NodeView(
            UUID id, String name, String host, int sshPort, String sshUser,
            NodeRole role, AgentStatus agentStatus, Instant lastHeartbeat, int lastConfigRevision,
            String systemInfoJson, Instant createdAt
    ) {}

    public record CreateNodeRequest(String name, String host, Integer sshPort, String sshUser, NodeRole role) {}
    public record UpdateNodeRequest(String name, String host, Integer sshPort, String sshUser, NodeRole role) {}
    public record AgentRegisterRequest(String name, String host, NodeRole role, String systemInfoJson) {}
    public record AgentHeartbeatRequest(String systemInfoJson, Integer configRevision) {}
}
