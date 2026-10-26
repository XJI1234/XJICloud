package com.xjicloud.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    boolean existsByPhone(String phone);

    Optional<UserAccount> findByPhone(String phone);

    Optional<UserAccount> findByUsername(String username);

    boolean existsByUsername(String username);
}
