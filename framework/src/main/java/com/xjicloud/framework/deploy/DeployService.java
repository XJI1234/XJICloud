package com.xjicloud.framework.deploy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.framework.configregistry.RuntimeConfigService;
import com.xjicloud.framework.node.ManagedNode;
import com.xjicloud.framework.node.NodeRole;
import com.xjicloud.framework.node.NodeService;
import com.xjicloud.framework.ssh.SshCommandService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeployService {

    private final DeployTaskRepository taskRepository;
    private final NodeService nodeService;
    private final SshCommandService sshService;
    private final RuntimeConfigService configService;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public DeployService(
            DeployTaskRepository taskRepository,
            NodeService nodeService,
            SshCommandService sshService,
            RuntimeConfigService configService,
            ObjectMapper objectMapper
    ) {
        this.taskRepository = taskRepository;
        this.nodeService = nodeService;
        this.sshService = sshService;
        this.configService = configService;
        this.objectMapper = objectMapper;
    }

    public List<DeployTaskView> listTasks() {
        return taskRepository.findAllByOrderByCreatedAtDesc().stream().map(DeployTaskView::from).toList();
    }

    @Transactional
    public DeployTaskView startDeploy(StartDeployRequest request, String username) {
        DeployTask task = new DeployTask();
        task.setType(request.type());
        task.setTargetNodeId(request.targetNodeId());
        task.setCreatedBy(username);
        try {
            task.setParamsJson(objectMapper.writeValueAsString(request.params()));
        } catch (Exception e) {
            task.setParamsJson("{}");
        }
        taskRepository.save(task);
        executor.submit(() -> runTask(task.getId()));
        return DeployTaskView.from(task);
    }

    public DeployTaskView getTask(UUID id) {
        return DeployTaskView.from(taskRepository.findById(id).orElseThrow());
    }

    private void runTask(UUID taskId) {
        DeployTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) return;
        task.setStatus(DeployStatus.RUNNING);
        task.setStartedAt(Instant.now());
        appendLog(task, "Deploy started: " + task.getType());
        taskRepository.save(task);
        try {
            ManagedNode node = nodeService.getNode(task.getTargetNodeId());
            switch (task.getType()) {
                case BASIC -> runBasic(task, node);
                case DOCKER -> runDocker(task, node);
                case K8S -> runK8s(task, node);
            }
            task.setStatus(DeployStatus.COMPLETED);
            appendLog(task, "Deploy completed successfully");
        } catch (Exception e) {
            task.setStatus(DeployStatus.FAILED);
            appendLog(task, "Deploy failed: " + e.getMessage());
        }
        task.setFinishedAt(Instant.now());
        taskRepository.save(task);
    }

    private void runBasic(DeployTask task, ManagedNode node) throws Exception {
        NodeRole role = node.getRole();
        String sourcePath = role == NodeRole.FRONTEND ? "/opt/xjicloud" : "/opt/xjicloud/backend";
        if (role == NodeRole.FRONTEND) {
            exec(task, node, "cd " + sourcePath + " && npm ci && npm run build:all:cloud");
            exec(task, node, "mkdir -p /var/www/xjicloud && cp -r " + sourcePath + "/dist/* /var/www/xjicloud/ && cp -r " + sourcePath + "/admin/dist /var/www/xjicloud/admin");
            exec(task, node, "nginx -s reload || true");
        } else if (role == NodeRole.BACKEND) {
            renderBackendEnv(task, node);
            exec(task, node, "cd " + sourcePath + " && mvn -DskipTests package");
            exec(task, node, "systemctl restart xjicloud-backend || true");
        } else {
            exec(task, node, "cd /opt/xjicloud/gpu-worker && docker build -t xjicloud/gpu-worker .");
        }
    }

    private void runDocker(DeployTask task, ManagedNode node) throws Exception {
        renderBackendEnv(task, node);
        exec(task, node, "cd /opt/xjicloud && docker compose -f deploy/docker-compose.prod.yml up -d --build");
        exec(task, node, "curl -sf http://127.0.0.1:8080/actuator/health || true");
    }

    private void runK8s(DeployTask task, ManagedNode node) throws Exception {
        exec(task, node, "cd /opt/xjicloud && kubectl apply -k deploy/k8s/");
        exec(task, node, "kubectl -n xjicloud rollout status deployment/backend --timeout=300s || true");
    }

    private void renderBackendEnv(DeployTask task, ManagedNode node) throws Exception {
        var snap = configService.getFullDocumentForBackend();
        String env = """
                SPRING_DATASOURCE_URL=%s
                SPRING_DATASOURCE_USERNAME=%s
                SPRING_DATASOURCE_PASSWORD=%s
                SPRING_DATA_REDIS_HOST=%s
                SPRING_DATA_REDIS_PORT=%s
                """.formatted(
                snap.database().url(),
                snap.database().username(),
                snap.database().password(),
                snap.redis().host(),
                snap.redis().port()
        );
        exec(task, node, "mkdir -p /etc/xjicloud && cat > /etc/xjicloud/backend.env << 'EOF'\n" + env + "\nEOF");
    }

    private void exec(DeployTask task, ManagedNode node, String command) throws Exception {
        appendLog(task, "$ " + command);
        var result = sshService.execute(node, command, 3600);
        if (!result.stdout().isBlank()) appendLog(task, result.stdout());
        if (!result.stderr().isBlank()) appendLog(task, result.stderr());
        if (result.exitCode() != 0) {
            throw new IllegalStateException("Command failed with exit " + result.exitCode());
        }
    }

    private void appendLog(DeployTask task, String line) {
        task.setLog((task.getLog() == null ? "" : task.getLog()) + line + "\n");
        taskRepository.save(task);
    }

    public record StartDeployRequest(DeployType type, UUID targetNodeId, Map<String, Object> params) {}
    public record DeployTaskView(
            UUID id, DeployType type, UUID targetNodeId, DeployStatus status,
            String log, String createdBy, Instant startedAt, Instant finishedAt, Instant createdAt
    ) {
        static DeployTaskView from(DeployTask t) {
            return new DeployTaskView(
                    t.getId(), t.getType(), t.getTargetNodeId(), t.getStatus(),
                    t.getLog(), t.getCreatedBy(), t.getStartedAt(), t.getFinishedAt(), t.getCreatedAt()
            );
        }
    }
}
