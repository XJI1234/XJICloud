package com.xjicloud.eci.instance;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** ECI 实例本地记录的 Spring Data 仓储。 */
public interface EciInstanceRepository extends JpaRepository<EciInstanceEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select instance from EciInstanceEntity instance where instance.id = :id")
    Optional<EciInstanceEntity> findForUpdateById(@Param("id") UUID id);

    @Query("select instance.taskId from EciInstanceEntity instance where instance.id = :id")
    Optional<UUID> findTaskIdById(@Param("id") UUID id);

    List<EciInstanceEntity> findByStatusOrderByCreatedAtDesc(InstanceStatus status);

    List<EciInstanceEntity> findAllByOrderByCreatedAtDesc();

    /** 启动补偿：按状态集合拉取未终态（及 RUNNING）记录。 */
    List<EciInstanceEntity> findByStatusIn(Collection<InstanceStatus> statuses);
}
