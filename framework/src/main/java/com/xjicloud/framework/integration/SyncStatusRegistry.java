package com.xjicloud.framework.integration;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class SyncStatusRegistry {

    private final Map<String, SyncEntry> entries = new ConcurrentHashMap<>();

    public void record(String clientId, int revision, Instant at) {
        entries.put(clientId, new SyncEntry(revision, at));
    }

    public Map<String, SyncEntry> all() {
        return Map.copyOf(entries);
    }

    public record SyncEntry(int revision, Instant syncedAt) {}
}
