package com.xjicloud.job;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingJobRepository extends JpaRepository<TrainingJob, UUID> {

    List<TrainingJob> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<TrainingJob> findAllByOrderByCreatedAtDesc();

    long countByStatus(JobStatus status);
}
