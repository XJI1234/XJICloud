package com.xjicloud.model;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ViewerConfigRepository extends JpaRepository<ViewerConfigEntity, UUID> {}
