package com.xjicloud.eci.provider;

import java.util.regex.Pattern;
import java.util.Set;
import java.util.regex.Matcher;

/** Safe provider status detail for API snapshots. */
public final class CloudStatusReason {

    private static final int MAX_LENGTH = 1024;
    private static final String HIDDEN_DETAIL = "云侧状态说明已隐藏";
    private static final Pattern SENSITIVE_DETAIL = Pattern.compile(
            "(?i)(?:\\b(?:https?|ftp)://"
                    + "|\\b(?:access[_ -]?key(?:[_ -]?(?:id|secret))?|secret(?:[_ -]?key)?|"
                    + "password|token|authorization|credential)\\b\\s*[:=]"
                    + "|\\b(?:AKID|LTAI)[A-Z0-9]{8,}\\b)");
    private static final Pattern ASSIGNMENT =
            Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*=");
    private static final Set<String> PROVIDER_LABELS = Set.of("reason", "msg", "exitCode", "event");

    private CloudStatusReason() {
    }

    public static String safe(String detail) {
        if (detail == null || detail.isBlank()) {
            return null;
        }
        String normalized = detail.trim();
        if (SENSITIVE_DETAIL.matcher(normalized).find() || hasUnapprovedAssignment(normalized)) {
            return HIDDEN_DETAIL;
        }
        return normalized.length() > MAX_LENGTH
                ? normalized.substring(0, MAX_LENGTH)
                : normalized;
    }

    private static boolean hasUnapprovedAssignment(String detail) {
        Matcher matcher = ASSIGNMENT.matcher(detail);
        while (matcher.find()) {
            if (!PROVIDER_LABELS.contains(matcher.group(1))) {
                return true;
            }
        }
        return false;
    }
}
