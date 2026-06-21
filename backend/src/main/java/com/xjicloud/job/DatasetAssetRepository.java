package com.xjicloud.job;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatasetAssetRepository extends JpaRepository<DatasetAsset, UUID> {

    List<DatasetAsset> findByJobIdOrderByFileNameAsc(UUID jobId);
}
