package com.xjicloud.eci.instance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** ECI 实例本地记录的 Spring Data 仓储。 */
public interface EciInstanceRepository extends JpaRepository<EciInstanceEntity, UUID> {

    List<EciInstanceEntity> findByStatusOrderByCreatedAtDesc(InstanceStatus status);

    List<EciInstanceEntity> findAllByOrderByCreatedAtDesc();
}
