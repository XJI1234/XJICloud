package com.xjicloud.admin;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CloudAccessKeyRepository extends JpaRepository<CloudAccessKey, UUID> {

    List<CloudAccessKey> findAllByOrderByCreatedAtDesc();

    @Query("""
            select distinct credential
            from CloudAccessKey credential
            join credential.resources resource
            where resource = :resource
            order by credential.createdAt desc
            """)
    List<CloudAccessKey> findAllByResourceOrderByCreatedAtDesc(@Param("resource") CloudResource resource);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);
}
