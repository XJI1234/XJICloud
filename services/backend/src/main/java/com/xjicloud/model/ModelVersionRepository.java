package com.xjicloud.model;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelVersionRepository extends JpaRepository<ModelVersionEntity, UUID> {

    List<ModelVersionEntity> findByModelIdOrderByVersionDesc(UUID modelId);

    boolean existsByModelId(UUID modelId);

    boolean existsByModelIdAndVersion(UUID modelId, int version);

    Optional<ModelVersionEntity> findByIdAndModelId(UUID id, UUID modelId);

    void deleteByModelId(UUID modelId);
}
