package com.xjicloud.admin;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "cloud_access_key")
public class CloudAccessKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 128)
    private String name;

    @Column(name = "access_key_id", nullable = false, length = 128)
    private String accessKeyId;

    @Column(nullable = false, length = 512)
    private String secret;

    @Column(length = 128)
    private String label;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "cloud_access_key_resource",
            joinColumns = @JoinColumn(name = "cloud_access_key_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "resource", nullable = false, length = 32)
    private Set<CloudResource> resources = new LinkedHashSet<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    Set<CloudResource> getResources() {
        return resources;
    }

    void setResources(Set<CloudResource> resources) {
        this.resources = new LinkedHashSet<>(resources);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

enum CloudResource {
    ECI_OPENAPI,
    OSS
}
