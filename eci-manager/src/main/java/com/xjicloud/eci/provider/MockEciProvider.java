package com.xjicloud.eci.provider;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * 本地 Mock：不调用阿里云。创建后状态视为 Running，ID 前缀 {@code mock-eci-}。
 * <p>
 * 支持两种终态注入：
 * <ul>
 *   <li>时间触发：{@code mockAutoCompleteSec > 0} 时，create 后到期在 {@link #describe} 惰性切到配置终态</li>
 *   <li>测试钩子：{@link #simulateStatus}（优先，之后不再被自动覆盖）</li>
 * </ul>
 */
public class MockEciProvider implements EciProvider {

    private static final Set<String> ALLOWED_SIMULATED = Set.of(
            "Pending", "Scheduling", "Running", "Succeeded", "Failed",
            "ScheduleFailed", "CreateFailed", "Deleted", "NotFound");

    private final int mockAutoCompleteSec;
    private final String mockAutoCompleteStatus;
    private final LongSupplier nowMillis;

    private final ConcurrentHashMap<String, String> statuses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> completeAtEpochMs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> manualOverride = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CreateSpec> createSpecs = new ConcurrentHashMap<>();
    private final AtomicReference<CreateSpec> lastCreateSpec = new AtomicReference<>();

    /** 单元测试默认：关闭时间触发，仅钩子。 */
    public MockEciProvider() {
        this(0, "Succeeded");
    }

    public MockEciProvider(int mockAutoCompleteSec, String mockAutoCompleteStatus) {
        this(mockAutoCompleteSec, mockAutoCompleteStatus, System::currentTimeMillis);
    }

    public MockEciProvider(int mockAutoCompleteSec, String mockAutoCompleteStatus, LongSupplier nowMillis) {
        this.mockAutoCompleteSec = mockAutoCompleteSec;
        this.mockAutoCompleteStatus = canonicalizeStatus(
                mockAutoCompleteStatus != null && !mockAutoCompleteStatus.isBlank()
                        ? mockAutoCompleteStatus
                        : "Succeeded");
        this.nowMillis = nowMillis != null ? nowMillis : System::currentTimeMillis;
    }

    public int getMockAutoCompleteSec() {
        return mockAutoCompleteSec;
    }

    public String getMockAutoCompleteStatus() {
        return mockAutoCompleteStatus;
    }

    @Override
    public String create(CreateSpec spec) {
        String id = "mock-eci-" + UUID.randomUUID();
        statuses.put(id, "Running");
        createSpecs.put(id, spec);
        lastCreateSpec.set(spec);
        manualOverride.remove(id);
        if (mockAutoCompleteSec > 0) {
            completeAtEpochMs.put(id, nowMillis.getAsLong() + mockAutoCompleteSec * 1000L);
        } else {
            completeAtEpochMs.remove(id);
        }
        return id;
    }

    @Override
    public CloudInstanceView describe(String cloudInstanceId) {
        maybeAutoComplete(cloudInstanceId);
        String status = statuses.getOrDefault(cloudInstanceId, "NotFound");
        return new CloudInstanceView(cloudInstanceId, status, null);
    }

    @Override
    public void delete(String cloudInstanceId) {
        statuses.put(cloudInstanceId, "Deleted");
        completeAtEpochMs.remove(cloudInstanceId);
    }

    /**
     * 测试钩子：将指定云实例切到目标云状态（如 {@code Succeeded} / {@code Failed}）。
     * 显式注入后不再被时间触发覆盖。
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
        String canonical = canonicalizeStatus(status);
        statuses.put(cloudInstanceId, canonical);
        manualOverride.put(cloudInstanceId, Boolean.TRUE);
        completeAtEpochMs.remove(cloudInstanceId);
    }

    /** 测试用：按云实例 ID 回读创建时的规格。 */
    public CreateSpec getCreateSpec(String cloudInstanceId) {
        return createSpecs.get(cloudInstanceId);
    }

    /** 测试用：最近一次 create 的规格。 */
    public CreateSpec getLastCreateSpec() {
        return lastCreateSpec.get();
    }

    private void maybeAutoComplete(String cloudInstanceId) {
        if (Boolean.TRUE.equals(manualOverride.get(cloudInstanceId))) {
            return;
        }
        Long due = completeAtEpochMs.get(cloudInstanceId);
        if (due == null || nowMillis.getAsLong() < due) {
            return;
        }
        String current = statuses.get(cloudInstanceId);
        if (current == null || "Deleted".equalsIgnoreCase(current) || "NotFound".equalsIgnoreCase(current)) {
            return;
        }
        statuses.put(cloudInstanceId, mockAutoCompleteStatus);
        completeAtEpochMs.remove(cloudInstanceId);
    }

    private static String canonicalizeStatus(String status) {
        String normalized = status.trim();
        boolean allowed = ALLOWED_SIMULATED.stream()
                .anyMatch(s -> s.equalsIgnoreCase(normalized));
        if (!allowed) {
            throw new IllegalArgumentException("unsupported mock status: " + status);
        }
        return ALLOWED_SIMULATED.stream()
                .filter(s -> s.equalsIgnoreCase(normalized))
                .findFirst()
                .orElse(normalized);
    }
}
