package com.xjicloud.worker;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerNodeRepository extends JpaRepository<WorkerNode, UUID> {

    List<WorkerNode> findAllByOrderByRegisteredAtDesc();
}
