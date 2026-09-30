package com.xjicloud.job;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingJobRepository extends JpaRepository<TrainingJob, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from TrainingJob job where job.id = :id")
    Optional<TrainingJob> findForUpdateById(@Param("id") UUID id);

    List<TrainingJob> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<TrainingJob> findAllByOrderByCreatedAtDesc();

    long countByStatus(JobStatus status);

    List<TrainingJob> findByStatusIn(Collection<JobStatus> statuses);

    List<TrainingJob> findByEciCancellationPendingTrueAndEciTaskIdIsNull();

    List<TrainingJob> findByEciTaskIdNotNullAndStatusIn(Collection<JobStatus> statuses);

    void deleteByProjectId(UUID projectId);
}
