package com.xjicloud.eci.provider;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ECI {@code /dev/shm} 大小：规范化为阿里云 EmptyDir {@code SizeLimit}（{@code 256Mi} / {@code 8Gi}），
 * 并校验必须大于默认 64Mi 且严格小于容器 memory。
 */
public final class ShmSize {

    public static final String VOLUME_NAME = "eci-shm";
    public static final String VOLUME_TYPE = "EmptyDirVolume";
    public static final String MEDIUM_MEMORY = "Memory";
    public static final String MOUNT_PATH = "/dev/shm";

    /** ECI 默认 shm；放大必须严格大于此值。 */
    static final long DEFAULT_SHM_BYTES = 64L * 1024 * 1024;

    private static final long BYTES_PER_MI = 1024L * 1024L;
    private static final long BYTES_PER_GI = 1024L * 1024L * 1024L;

    private static final Pattern PATTERN = Pattern.compile("^(\\d+)(Mi|Gi|M|G)$", Pattern.CASE_INSENSITIVE);

    private ShmSize() {
    }

    /**
     * 请求值优先，否则用配置默认；二者皆空则返回 {@code null}（不挂 EmptyDir，保留 64Mi）。
     *
     * @throws IllegalArgumentException 格式非法、不大于 64Mi、或不小于容器 memory
     */
    public static String resolve(String requestValue, String defaultValue, double memoryGiB) {
        String raw = firstNonBlank(requestValue, defaultValue);
        if (raw == null) {
            return null;
        }
        String canonical = normalize(raw);
        long bytes = toBytes(canonical);
        if (bytes <= DEFAULT_SHM_BYTES) {
            throw new IllegalArgumentException("shmSize 必须大于 64Mi（ECI 默认大小），当前=" + canonical);
        }
        if (!(memoryGiB > 0)) {
            throw new IllegalArgumentException("memory 须为正数才能设置 shmSize");
        }
        double memoryBytes = memoryGiB * BYTES_PER_GI;
        if (bytes >= memoryBytes) {
            throw new IllegalArgumentException(
                    "shmSize 必须严格小于容器 memory（GiB），shmSize=" + canonical + " memory=" + memoryGiB);
        }
        return canonical;
    }

    /** 将 {@code 8G}/{@code 256M} 规范为阿里云 {@code 8Gi}/{@code 256Mi}。 */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("shmSize 不能为空");
        }
        Matcher matcher = PATTERN.matcher(raw.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("shmSize 须为带单位的大小，如 256Mi 或 8Gi（也接受 256M / 8G）");
        }
        long amount = Long.parseLong(matcher.group(1));
        if (amount <= 0) {
            throw new IllegalArgumentException("shmSize 须为正数");
        }
        String unit = matcher.group(2).toLowerCase(Locale.ROOT);
        if ("g".equals(unit) || "gi".equals(unit)) {
            return amount + "Gi";
        }
        return amount + "Mi";
    }

    public static long toBytes(String canonical) {
        String normalized = normalize(canonical);
        if (normalized.endsWith("Gi")) {
            return Long.parseLong(normalized.substring(0, normalized.length() - 2)) * BYTES_PER_GI;
        }
        return Long.parseLong(normalized.substring(0, normalized.length() - 2)) * BYTES_PER_MI;
    }

    private static String firstNonBlank(String requestValue, String defaultValue) {
        if (requestValue != null && !requestValue.isBlank()) {
            return requestValue.trim();
        }
        if (defaultValue != null && !defaultValue.isBlank()) {
            return defaultValue.trim();
        }
        return null;
    }
}
