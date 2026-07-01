package com.xjicloud.framework.deploy;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeployTaskRepository extends JpaRepository<DeployTask, UUID> {
    List<DeployTask> findAllByOrderByCreatedAtDesc();
}
