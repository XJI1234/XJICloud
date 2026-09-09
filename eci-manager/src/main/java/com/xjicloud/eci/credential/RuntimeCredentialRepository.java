package com.xjicloud.eci.credential;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RuntimeCredentialRepository extends JpaRepository<RuntimeCredentialEntity, String> {
}
