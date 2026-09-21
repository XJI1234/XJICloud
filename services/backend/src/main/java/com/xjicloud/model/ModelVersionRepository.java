package com.xjicloud.model;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ModelVersionRepository extends JpaRepository<ModelVersionEntity, UUID> {

    List<ModelVersionEntity> findByModelIdOrderByVersionDesc(UUID modelId);

    boolean existsByModelId(UUID modelId);

    boolean existsByModelIdAndVersion(UUID modelId, int version);

    Optional<ModelVersionEntity> findByIdAndModelId(UUID id, UUID modelId);

    @Transactional
    void deleteByModelId(UUID modelId);
}
