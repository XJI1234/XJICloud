package com.xjicloud.framework.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FrameworkUserRepository extends JpaRepository<FrameworkUser, UUID> {
    Optional<FrameworkUser> findByUsername(String username);
}
