package com.xjicloud.eci.credential;

import com.xjicloud.eci.common.BusinessException;
import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.credential.dto.HttpApiKeyStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HTTP Api-Key 双钥：种子、轮换、重叠、过期、revoke。Repository 用内存 KV，不 mock SUT。
 */
class HttpApiKeyStoreTest {

    private static final Instant T0 = Instant.parse("2026-09-09T00:00:00Z");
    private static final String SEED = "seed-http-api-key";
    private static final String NEXT = "next-http-api-key";
    private static final String KEY_CURRENT = "eci.api-key";
    private static final String KEY_PREVIOUS = "eci.api-key.previous";
    private static final String KEY_EXPIRES = "eci.api-key.previous-expires-at";

    private MapRuntimeConfigStore config;
    private EciProperties properties;
    private MutableClock clock;
    private HttpApiKeyStore store;

    @BeforeEach
    void setUp() {
        config = new MapRuntimeConfigStore();
        properties = new EciProperties();
        properties.setApiKey(SEED);
        properties.setApiKeyOverlapSec(3600);
        clock = new MutableClock(T0);
        store = new HttpApiKeyStore(config, properties, clock);
        store.afterSingletonsInstantiated();
    }

    @Test
    void seedWritesPropertiesWhenRowAbsentThenAcceptsSeed() {
        assertThat(config.get(KEY_CURRENT)).contains(SEED);
        assertThat(store.hasCurrent()).isTrue();
        assertThat(store.accepts(SEED)).isTrue();
        assertThat(store.accepts(NEXT)).isFalse();
    }

    @Test
    void seedDoesNotOverwriteExistingCurrent() {
        config.put(KEY_CURRENT, "stored-http-key");
        properties.setApiKey("env-http-api-key");
        store = new HttpApiKeyStore(config, properties, clock);
        store.afterSingletonsInstantiated();

        assertThat(store.accepts("stored-http-key")).isTrue();
        assertThat(store.accepts("env-http-api-key")).isFalse();
        assertThat(config.get(KEY_CURRENT)).contains("stored-http-key");
    }

    @Test
    void rotateMakesNewKeyAcceptedImmediatelyAndOldKeyDuringOverlap() {
        HttpApiKeyStatus status = store.rotate(NEXT);

        assertThat(status.getApiKeyHint()).isEqualTo(CredentialHint.mask(NEXT));
        assertThat(status.isPreviousActive()).isTrue();
        assertThat(status.getPreviousExpiresAt()).isEqualTo(T0.plusSeconds(3600));
        assertThat(store.accepts(NEXT)).isTrue();
        assertThat(store.accepts(SEED)).isTrue();
        assertThat(config.get(KEY_CURRENT)).contains(NEXT);
        assertThat(config.get(KEY_PREVIOUS)).contains(SEED);
        assertThat(config.get(KEY_EXPIRES)).contains(T0.plusSeconds(3600).toString());
    }

    @Test
    void previousRejectedAfterOverlapExpires() {
        store.rotate(NEXT);
        clock.setInstant(T0.plusSeconds(3600));

        assertThat(store.accepts(NEXT)).isTrue();
        assertThat(store.accepts(SEED)).isFalse();
        assertThat(store.status().isPreviousActive()).isFalse();
        assertThat(store.status().getPreviousExpiresAt()).isNull();
    }

    @Test
    void overlapZeroClearsPreviousImmediately() {
        properties.setApiKeyOverlapSec(0);
        store = new HttpApiKeyStore(config, properties, clock);
        store.afterSingletonsInstantiated();

        HttpApiKeyStatus status = store.rotate(NEXT);

        assertThat(status.isPreviousActive()).isFalse();
        assertThat(status.getPreviousExpiresAt()).isNull();
        assertThat(store.accepts(NEXT)).isTrue();
        assertThat(store.accepts(SEED)).isFalse();
        assertThat(config.exists(KEY_PREVIOUS)).isFalse();
        assertThat(config.exists(KEY_EXPIRES)).isFalse();
    }

    @Test
    void sameKeyIsNoOpAndKeepsPrevious() {
        store.rotate(NEXT);
        Instant expires = store.status().getPreviousExpiresAt();

        HttpApiKeyStatus again = store.rotate("  " + NEXT + "  ");

        assertThat(again.getApiKeyHint()).isEqualTo(CredentialHint.mask(NEXT));
        assertThat(again.isPreviousActive()).isTrue();
        assertThat(again.getPreviousExpiresAt()).isEqualTo(expires);
        assertThat(config.get(KEY_PREVIOUS)).contains(SEED);
        assertThat(store.accepts(SEED)).isTrue();
    }

    @Test
    void revokeRejectsPrevious() {
        store.rotate(NEXT);
        HttpApiKeyStatus revoked = store.revokePrevious();

        assertThat(revoked.isPreviousActive()).isFalse();
        assertThat(revoked.getPreviousExpiresAt()).isNull();
        assertThat(revoked.getApiKeyHint()).isEqualTo(CredentialHint.mask(NEXT));
        assertThat(store.accepts(NEXT)).isTrue();
        assertThat(store.accepts(SEED)).isFalse();
        assertThat(config.exists(KEY_PREVIOUS)).isFalse();
    }

    @Test
    void revokeWithoutPreviousIsIdempotent() {
        HttpApiKeyStatus revoked = store.revokePrevious();

        assertThat(revoked.isPreviousActive()).isFalse();
        assertThat(revoked.getPreviousExpiresAt()).isNull();
        assertThat(revoked.getApiKeyHint()).isEqualTo(CredentialHint.mask(SEED));
        assertThat(store.accepts(SEED)).isTrue();

        HttpApiKeyStatus again = store.revokePrevious();
        assertThat(again.isPreviousActive()).isFalse();
        assertThat(store.accepts(SEED)).isTrue();
    }

    @Test
    void rotateBlankThrows400() {
        assertThatThrownBy(() -> store.rotate("   "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("apiKey")
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> store.rotate(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("apiKey");
        assertThatThrownBy(() -> store.rotate(""))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("apiKey");
    }

    @Test
    void acceptsRejectsNullBlankWhitespaceAndWrongKey() {
        assertThat(store.accepts(SEED)).isTrue();
        assertThat(store.accepts(" " + SEED)).isFalse();
        assertThat(store.accepts(SEED + " ")).isFalse();
        assertThat(store.accepts(null)).isFalse();
        assertThat(store.accepts("")).isFalse();
        assertThat(store.accepts("seed-http-api-kez")).isFalse();
        assertThat(store.accepts("short")).isFalse();
        assertThat(store.hasCurrent()).isTrue();
    }

    @Test
    void hasCurrentFalseWhenUnseededAndPropertiesEmpty() {
        config.delete(KEY_CURRENT);
        properties.setApiKey("");
        store = new HttpApiKeyStore(config, properties, clock);
        store.afterSingletonsInstantiated();

        assertThat(store.hasCurrent()).isFalse();
        assertThat(store.accepts(SEED)).isFalse();
        assertThat(config.exists(KEY_CURRENT)).isFalse();
    }

    @Test
    void corruptExpiresAtDoesNotAcceptPrevious() {
        store.rotate(NEXT);
        config.put(KEY_EXPIRES, "not-an-instant");
        store.afterSingletonsInstantiated();

        assertThat(store.accepts(NEXT)).isTrue();
        assertThat(store.accepts(SEED)).isFalse();
        assertThat(store.status().isPreviousActive()).isFalse();
    }

    static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
