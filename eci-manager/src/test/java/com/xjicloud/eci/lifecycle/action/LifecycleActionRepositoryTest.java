package com.xjicloud.eci.lifecycle.action;

import com.xjicloud.eci.config.EciProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eci-lifecycle-action-repository;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "logging.level.org.hibernate.engine.jdbc.spi.SqlExceptionHelper=OFF"
})
class LifecycleActionRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");

    @Autowired
    private LifecycleActionRepository repository;

    @Autowired
    private EciProperties properties;

    @Test
    void bindsLifecycleActionDefaultsFromApplicationConfiguration() {
        assertThat(properties.getLifecycleAction().getDispatchIntervalMs()).isEqualTo(1000);
        assertThat(properties.getLifecycleAction().getBatchSize()).isEqualTo(20);
        assertThat(properties.getLifecycleAction().getLeaseSec()).isEqualTo(60);
        assertThat(properties.getLifecycleAction().getRetryInitialSec()).isEqualTo(2);
        assertThat(properties.getLifecycleAction().getRetryMaxSec()).isEqualTo(300);
        assertThat(properties.getLifecycleAction().getCleanupAlertAttempts()).isEqualTo(5);
    }

    @Test
    void createActionIsUniquePerInstanceAndType() {
        UUID instanceId = UUID.randomUUID();
        repository.saveAndFlush(action(instanceId, LifecycleActionType.CREATE,
                LifecycleActionStatus.READY, NOW, NOW, null));

        assertThatThrownBy(() -> repository.saveAndFlush(action(instanceId,
                LifecycleActionType.CREATE, LifecycleActionStatus.READY, NOW, NOW, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsActionByInstanceAndType() {
        UUID instanceId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        repository.saveAndFlush(action(instanceId, LifecycleActionType.CREATE,
                LifecycleActionStatus.READY, NOW, NOW, null, actionId));

        assertThat(repository.findByInstanceIdAndActionType(
                instanceId, LifecycleActionType.CREATE).orElseThrow().getId()).isEqualTo(actionId);
        assertThat(repository.findByInstanceIdAndActionType(
                instanceId, LifecycleActionType.DELETE)).isEmpty();
    }

    @Test
    void findsOnlyEnabledReadyActionsThatAreDueOrHaveExpiredLeases() {
        UUID readyDue = save(LifecycleActionType.CREATE, LifecycleActionStatus.READY,
                NOW.minusSeconds(30), NOW.minusSeconds(6), null);
        UUID waitingDue = save(LifecycleActionType.CREATE, LifecycleActionStatus.WAITING,
                NOW.minusSeconds(20), NOW.minusSeconds(5), null);
        UUID expiredLease = save(LifecycleActionType.CREATE, LifecycleActionStatus.IN_PROGRESS,
                NOW.plusSeconds(30), NOW.minusSeconds(4), NOW.minusSeconds(1));

        save(LifecycleActionType.CREATE, LifecycleActionStatus.READY,
                NOW.plusSeconds(1), NOW.minusSeconds(3), null);
        save(LifecycleActionType.CREATE, LifecycleActionStatus.IN_PROGRESS,
                NOW.minusSeconds(10), NOW.minusSeconds(2), NOW.plusSeconds(1));
        save(LifecycleActionType.CREATE, LifecycleActionStatus.SUCCEEDED,
                NOW.minusSeconds(10), NOW.minusSeconds(1), null);
        save(LifecycleActionType.DELETE, LifecycleActionStatus.READY,
                NOW.minusSeconds(10), NOW, null);

        List<UUID> dueIds = repository.findDueIds(NOW, Set.of(LifecycleActionType.CREATE),
                Set.of(LifecycleActionStatus.READY, LifecycleActionStatus.WAITING),
                LifecycleActionStatus.IN_PROGRESS, PageRequest.of(0, 20));

        assertThat(dueIds).containsExactly(readyDue, waitingDue, expiredLease);
    }

    @Test
    void claimIsConditionalAndReleaseRejectsAnOldLeaseToken() {
        UUID actionId = save(LifecycleActionType.DELETE, LifecycleActionStatus.READY,
                NOW, NOW.minusSeconds(1), null);
        UUID leaseToken = UUID.randomUUID();
        Instant leaseUntil = NOW.plusSeconds(60);

        assertThat(repository.claim(actionId, leaseToken, NOW, leaseUntil,
                Set.of(LifecycleActionStatus.READY, LifecycleActionStatus.WAITING),
                LifecycleActionStatus.IN_PROGRESS)).isEqualTo(1);
        assertThat(repository.claim(actionId, UUID.randomUUID(), NOW, leaseUntil,
                Set.of(LifecycleActionStatus.READY, LifecycleActionStatus.WAITING),
                LifecycleActionStatus.IN_PROGRESS)).isZero();

        Instant retryAt = NOW.plusSeconds(1);
        assertThat(repository.releaseExpiredLeases(actionId, UUID.randomUUID(), retryAt, NOW,
                LifecycleActionStatus.WAITING, LifecycleActionStatus.IN_PROGRESS)).isZero();
        assertThat(repository.releaseExpiredLeases(actionId, leaseToken, retryAt, NOW,
                LifecycleActionStatus.WAITING, LifecycleActionStatus.IN_PROGRESS))
                .isEqualTo(1);

        LifecycleActionEntity released = repository.findById(actionId).orElseThrow();
        assertThat(released.getStatus()).isEqualTo(LifecycleActionStatus.WAITING);
        assertThat(released.getLeaseToken()).isNull();
        assertThat(released.getLeaseUntil()).isNull();
        assertThat(released.getNextAttemptAt()).isEqualTo(retryAt);
        assertThat(released.getUpdatedAt()).isEqualTo(NOW);
    }

    private UUID save(
            LifecycleActionType type,
            LifecycleActionStatus status,
            Instant nextAttemptAt,
            Instant createdAt,
            Instant leaseUntil) {
        UUID id = UUID.randomUUID();
        repository.saveAndFlush(action(UUID.randomUUID(), type, status,
                nextAttemptAt, createdAt, leaseUntil, id));
        return id;
    }

    private static LifecycleActionEntity action(
            UUID instanceId,
            LifecycleActionType type,
            LifecycleActionStatus status,
            Instant nextAttemptAt,
            Instant createdAt,
            Instant leaseUntil) {
        return action(instanceId, type, status, nextAttemptAt, createdAt, leaseUntil,
                UUID.randomUUID());
    }

    private static LifecycleActionEntity action(
            UUID instanceId,
            LifecycleActionType type,
            LifecycleActionStatus status,
            Instant nextAttemptAt,
            Instant createdAt,
            Instant leaseUntil,
            UUID id) {
        LifecycleActionEntity action = new LifecycleActionEntity();
        action.setId(id);
        action.setInstanceId(instanceId);
        action.setActionType(type);
        action.setPhase(type == LifecycleActionType.CREATE
                ? LifecycleActionPhase.CREATE_REQUEST : LifecycleActionPhase.DELETE_REQUEST);
        action.setStatus(status);
        action.setAttemptCount(0);
        action.setNextAttemptAt(nextAttemptAt);
        action.setLeaseUntil(leaseUntil);
        action.setCreatedAt(createdAt);
        action.setUpdatedAt(createdAt);
        return action;
    }
}
