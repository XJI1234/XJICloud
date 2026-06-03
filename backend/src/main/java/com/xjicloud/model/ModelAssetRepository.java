package com.xjicloud.model;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelAssetRepository extends JpaRepository<ModelAsset, UUID> {

    List<ModelAsset> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
