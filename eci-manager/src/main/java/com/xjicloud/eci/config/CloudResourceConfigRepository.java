package com.xjicloud.eci.config;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface CloudResourceConfigRepository extends JpaRepository<CloudResourceConfigEntity, String> {

    /** 删除整类覆盖值；该分类没有覆盖行时同样安全。 */
    void deleteByConfigKeyIn(Collection<String> configKeys);
}
