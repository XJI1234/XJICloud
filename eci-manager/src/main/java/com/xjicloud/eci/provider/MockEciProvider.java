package com.xjicloud.eci.provider;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地 Mock：不调用阿里云。创建后状态视为 Running，ID 前缀 {@code mock-eci-}。
 */
public class MockEciProvider implements EciProvider {

    private final ConcurrentHashMap<String, String> statuses = new ConcurrentHashMap<>();

    @Override
    public String create(CreateSpec spec) {
        String id = "mock-eci-" + UUID.randomUUID();
        statuses.put(id, "Running");
        return id;
    }

    @Override
    public CloudInstanceView describe(String cloudInstanceId) {
        String status = statuses.getOrDefault(cloudInstanceId, "NotFound");
        return new CloudInstanceView(cloudInstanceId, status, null);
    }

    @Override
    public void delete(String cloudInstanceId) {
        statuses.put(cloudInstanceId, "Deleted");
    }
}
