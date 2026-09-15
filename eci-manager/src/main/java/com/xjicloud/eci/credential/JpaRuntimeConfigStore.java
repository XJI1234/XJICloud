package com.xjicloud.eci.credential;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@link RuntimeConfigStore} 的 JPA 实现，表 {@code eci_runtime_config}。
 */
@Component
public class JpaRuntimeConfigStore implements RuntimeConfigStore {

    private final RuntimeCredentialRepository repository;

    public JpaRuntimeConfigStore(RuntimeCredentialRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean exists(String key) {
        return repository.existsById(key);
    }

    @Override
    public Optional<String> get(String key) {
        return repository.findById(key).map(RuntimeCredentialEntity::getConfigValue);
    }

    @Override
    public void put(String key, String value) {
        RuntimeCredentialEntity entity = repository.findById(key).orElseGet(() -> new RuntimeCredentialEntity(key, value));
        entity.setConfigValue(value);
        repository.save(entity);
    }

    @Override
    public void delete(String key) {
        if (repository.existsById(key)) {
            repository.deleteById(key);
        }
    }
}
