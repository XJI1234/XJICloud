package com.xjicloud.admin.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CloudAccessKeyDtos {

    private CloudAccessKeyDtos() {
    }

    public record CloudAccessKeyListResponse(
            String resource,
            UUID currentId,
            List<CloudAccessKeyResponse> items
    ) {
    }

    public record CloudAccessKeyResponse(
            UUID id,
            String name,
            String accessKeyHint,
            boolean secretConfigured,
            List<String> resources,
            List<String> selectedResources,
            Instant createdAt,
            Instant updatedAt,
            String label
    ) {
        public CloudAccessKeyResponse(
                UUID id,
                String name,
                String accessKeyHint,
                boolean secretConfigured,
                List<String> resources,
                List<String> selectedResources,
                Instant createdAt,
                Instant updatedAt
        ) {
            this(id, name, accessKeyHint, secretConfigured, resources, selectedResources, createdAt, updatedAt, "");
        }
    }

    public record CloudAccessKeySelectResponse(
            UUID id,
            String name,
            String accessKeyHint,
            boolean secretConfigured,
            List<String> resources,
            List<String> selectedResources,
            Instant createdAt,
            Instant updatedAt,
            String appliedResource,
            String label
    ) {
        public CloudAccessKeySelectResponse(
                UUID id,
                String name,
                String accessKeyHint,
                boolean secretConfigured,
                List<String> resources,
                List<String> selectedResources,
                Instant createdAt,
                Instant updatedAt,
                String appliedResource
        ) {
            this(
                    id,
                    name,
                    accessKeyHint,
                    secretConfigured,
                    resources,
                    selectedResources,
                    createdAt,
                    updatedAt,
                    appliedResource,
                    ""
            );
        }
    }

    public record CreateCloudAccessKeyRequest(
            String name,
            String accessKeyId,
            String accessKeySecret,
            List<String> resources,
            String label
    ) {
        public CreateCloudAccessKeyRequest(
                String name,
                String accessKeyId,
                String accessKeySecret,
                List<String> resources
        ) {
            this(name, accessKeyId, accessKeySecret, resources, null);
        }
    }

    public record SelectCloudAccessKeyRequest(String resource) {
    }

    public record TestCloudAccessKeyRequest(
            String resource,
            String accessKeyId,
            String accessKeySecret
    ) {
    }

    public record TestCloudAccessKeyResponse(
            String resource,
            boolean ok,
            String accessKeyHint,
            String detail,
            boolean persistedCurrent,
            boolean appliedToEciManager
    ) {
    }

    public record UpdateCloudAccessKeyRequest(
            String name,
            String accessKeyId,
            String accessKeySecret,
            List<String> resources,
            String label
    ) {
        public UpdateCloudAccessKeyRequest(
                String name,
                String accessKeyId,
                String accessKeySecret,
                List<String> resources
        ) {
            this(name, accessKeyId, accessKeySecret, resources, null);
        }
    }
}
