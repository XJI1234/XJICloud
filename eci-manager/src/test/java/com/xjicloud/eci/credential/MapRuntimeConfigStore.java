package com.xjicloud.eci.credential;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 进程内 KV，供 HttpApiKeyStore 单测使用。不 mock Repository。
 */
public final class MapRuntimeConfigStore implements RuntimeConfigStore {

    private final Map<String, String> values = new HashMap<>();

    @Override
    public boolean exists(String key) {
        return values.containsKey(key);
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(values.get(key));
    }

    @Override
    public void put(String key, String value) {
        values.put(key, value);
    }

    @Override
    public void delete(String key) {
        values.remove(key);
    }
}
