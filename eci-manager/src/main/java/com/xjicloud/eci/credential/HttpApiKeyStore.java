package com.xjicloud.eci.credential;

import com.xjicloud.eci.common.BusinessException;
import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.credential.dto.HttpApiKeyStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * HTTP Api-Key 当前/上一把与重叠窗口。与云当前钥独立。GET 路径不经过本类。
 */
@Service
public class HttpApiKeyStore implements SmartInitializingSingleton {

    private static final String KEY_CURRENT = "eci.api-key";
    private static final String KEY_PREVIOUS = "eci.api-key.previous";
    private static final String KEY_EXPIRES_AT = "eci.api-key.previous-expires-at";

    private static final Logger log = LoggerFactory.getLogger(HttpApiKeyStore.class);

    private final RuntimeConfigStore config;
    private final EciProperties properties;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile Snapshot snapshot = Snapshot.EMPTY;

    public HttpApiKeyStore(RuntimeConfigStore config, EciProperties properties, Clock clock) {
        this.config = config;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void afterSingletonsInstantiated() {
        seedIfAbsent();
        reloadSnapshot();
    }

    public void seedIfAbsent() {
        if (config.exists(KEY_CURRENT) || !StringUtils.hasText(properties.getApiKey())) {
            return;
        }
        config.put(KEY_CURRENT, properties.getApiKey().trim());
    }

    public boolean hasCurrent() {
        return StringUtils.hasText(snapshot.current);
    }

    public boolean accepts(String provided) {
        Snapshot snap = snapshot;
        if (!StringUtils.hasText(provided) || !StringUtils.hasText(snap.current)) {
            return false;
        }
        byte[] providedBytes = utf8(provided);
        boolean currentOk = MessageDigest.isEqual(utf8(snap.current), providedBytes);
        boolean previousInWindow = StringUtils.hasText(snap.previous)
                && snap.expiresAt != null
                && clock.instant().isBefore(snap.expiresAt);
        boolean previousOk = previousInWindow && MessageDigest.isEqual(utf8(snap.previous), providedBytes);
        return currentOk | previousOk;
    }

    public HttpApiKeyStatus status() {
        Snapshot snap = snapshot;
        Instant expiresAt = previousActive(snap) ? snap.expiresAt : null;
        return new HttpApiKeyStatus(CredentialHint.mask(snap.current), previousActive(snap), expiresAt);
    }

    public HttpApiKeyStatus rotate(String apiKey) {
        lock.lock();
        try {
            String trimmed = apiKey == null ? "" : apiKey.trim();
            if (!StringUtils.hasText(trimmed)) {
                throw new BusinessException("apiKey 必填且不能为空白");
            }
            reloadSnapshot();
            if (hasCurrent() && constantTimeEquals(snapshot.current, trimmed)) {
                return status();
            }
            String oldCurrent = snapshot.current;
            int overlapSec = properties.getApiKeyOverlapSec();
            write(KEY_CURRENT, trimmed);
            if (StringUtils.hasText(oldCurrent) && overlapSec > 0) {
                Instant expiresAt = clock.instant().plusSeconds(overlapSec);
                write(KEY_PREVIOUS, oldCurrent);
                write(KEY_EXPIRES_AT, expiresAt.toString());
            } else {
                delete(KEY_PREVIOUS);
                delete(KEY_EXPIRES_AT);
            }
            reloadSnapshot();
            HttpApiKeyStatus result = status();
            log.info("http api-key rotated hint={} previousActive={}",
                    result.getApiKeyHint(), result.isPreviousActive());
            return result;
        } finally {
            lock.unlock();
        }
    }

    public HttpApiKeyStatus revokePrevious() {
        lock.lock();
        try {
            delete(KEY_PREVIOUS);
            delete(KEY_EXPIRES_AT);
            reloadSnapshot();
            HttpApiKeyStatus result = status();
            log.info("http api-key previous revoked hint={} previousActive={}",
                    result.getApiKeyHint(), result.isPreviousActive());
            return result;
        } finally {
            lock.unlock();
        }
    }

    private void reloadSnapshot() {
        String current = read(KEY_CURRENT);
        String previous = read(KEY_PREVIOUS);
        Instant expiresAt = parseInstant(read(KEY_EXPIRES_AT));
        snapshot = new Snapshot(current, previous, expiresAt);
    }

    private boolean previousActive(Snapshot snap) {
        return StringUtils.hasText(snap.previous)
                && snap.expiresAt != null
                && clock.instant().isBefore(snap.expiresAt);
    }

    private String read(String key) {
        return config.get(key).orElse("");
    }

    private void write(String key, String value) {
        config.put(key, value);
    }

    private void delete(String key) {
        config.delete(key);
    }

    private static Instant parseInstant(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Instant.parse(raw.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(utf8(expected), utf8(actual));
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private record Snapshot(String current, String previous, Instant expiresAt) {
        static final Snapshot EMPTY = new Snapshot("", "", null);
    }
}
