package com.xjicloud.eci.provider;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 本地 Mock：不调用阿里云。创建后状态视为 Running，ID 前缀 {@code mock-eci-}。
 * <p>
 * 可通过 {@link #simulateStatus} 注入 Succeeded / Failed 等终态，供任务生命周期测试使用。
 */
public class MockEciProvider implements EciProvider {

    private static final Set<String> ALLOWED_SIMULATED = Set.of(
            "Pending", "Scheduling", "Running", "Succeeded", "Failed",
            "ScheduleFailed", "CreateFailed", "Deleted", "NotFound");

    private final ConcurrentHashMap<String, String> statuses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CreateSpec> createSpecs = new ConcurrentHashMap<>();
    private final AtomicReference<CreateSpec> lastCreateSpec = new AtomicReference<>();

    @Override
    public String create(CreateSpec spec) {
        String id = "mock-eci-" + UUID.randomUUID();
        statuses.put(id, "Running");
        createSpecs.put(id, spec);
        lastCreateSpec.set(spec);
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

    /**
     * 测试钩子：将指定云实例切到目标云状态（如 {@code Succeeded} / {@code Failed}）。
     *
     * @throws IllegalArgumentException 未知 cloudId 或不支持的状态名
     */
    public void simulateStatus(String cloudInstanceId, String status) {
        if (!statuses.containsKey(cloudInstanceId) && !"NotFound".equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("unknown mock cloudInstanceId: " + cloudInstanceId);
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status must not be blank");
        }
        String normalized = status.trim();
        boolean allowed = ALLOWED_SIMULATED.stream()
                .anyMatch(s -> s.equalsIgnoreCase(normalized));
        if (!allowed) {
            throw new IllegalArgumentException("unsupported mock status: " + status);
        }
        // 统一成首字母大写形式，与阿里云描述风格接近
        String canonical = ALLOWED_SIMULATED.stream()
                .filter(s -> s.equalsIgnoreCase(normalized))
                .findFirst()
                .orElse(normalized);
        statuses.put(cloudInstanceId, canonical);
    }

    /** 测试用：按云实例 ID 回读创建时的规格。 */
    public CreateSpec getCreateSpec(String cloudInstanceId) {
        return createSpecs.get(cloudInstanceId);
    }

    /** 测试用：最近一次 create 的规格。 */
    public CreateSpec getLastCreateSpec() {
        return lastCreateSpec.get();
    }
}
