package com.xjicloud.framework.node;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "managed_nodes")
public class ManagedNode {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 256)
    private String host;

    private int sshPort = 22;

    @Column(length = 64)
    private String sshUser = "root";

    @Enumerated(EnumType.STRING)
    private NodeRole role = NodeRole.CUSTOM;

    @Enumerated(EnumType.STRING)
    private AgentStatus agentStatus = AgentStatus.OFFLINE;

    private Instant lastHeartbeat;

    private int lastConfigRevision;

    @Column(length = 4096)
    private String systemInfoJson;

    @Column(length = 1024)
    private String deployCapabilitiesJson;

    @Column(length = 4096)
    private String deployProfileJson;

    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getSshPort() { return sshPort; }
    public void setSshPort(int sshPort) { this.sshPort = sshPort; }
    public String getSshUser() { return sshUser; }
    public void setSshUser(String sshUser) { this.sshUser = sshUser; }
    public NodeRole getRole() { return role; }
    public void setRole(NodeRole role) { this.role = role; }
    public AgentStatus getAgentStatus() { return agentStatus; }
    public void setAgentStatus(AgentStatus agentStatus) { this.agentStatus = agentStatus; }
    public Instant getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(Instant lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
    public int getLastConfigRevision() { return lastConfigRevision; }
    public void setLastConfigRevision(int lastConfigRevision) { this.lastConfigRevision = lastConfigRevision; }
    public String getSystemInfoJson() { return systemInfoJson; }
    public void setSystemInfoJson(String systemInfoJson) { this.systemInfoJson = systemInfoJson; }
    public String getDeployCapabilitiesJson() { return deployCapabilitiesJson; }
    public void setDeployCapabilitiesJson(String deployCapabilitiesJson) { this.deployCapabilitiesJson = deployCapabilitiesJson; }
    public String getDeployProfileJson() { return deployProfileJson; }
    public void setDeployProfileJson(String deployProfileJson) { this.deployProfileJson = deployProfileJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
