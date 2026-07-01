package com.xjicloud.framework.eci;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EciInstanceRepository extends JpaRepository<EciInstance, UUID> {
    List<EciInstance> findAllByOrderByCreatedAtDesc();
    List<EciInstance> findByStatusIn(List<EciStatus> statuses);
}
