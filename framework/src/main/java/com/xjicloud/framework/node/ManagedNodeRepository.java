package com.xjicloud.framework.node;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedNodeRepository extends JpaRepository<ManagedNode, UUID> {
    List<ManagedNode> findAllByOrderByCreatedAtDesc();
    Optional<ManagedNode> findByHost(String host);
}
