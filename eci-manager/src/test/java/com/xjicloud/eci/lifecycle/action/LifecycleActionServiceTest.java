package com.xjicloud.eci.lifecycle.action;

import com.xjicloud.eci.config.AsyncConfig;
import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.instance.EciInstanceEntity;
import com.xjicloud.eci.instance.EciInstanceRepository;
import com.xjicloud.eci.instance.InstanceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eci-lifecycle-action-service;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "logging.level.org.hibernate.engine.jdbc.spi.SqlExceptionHelper=OFF"
})
@Import({LifecycleActionService.class, AsyncConfig.class, LifecycleActionServiceTest.FixedClockConfig.class})
class LifecycleActionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");
    private static final Instant CLOCK_NOW = Instant.parse("2026-09-21T12:30:00Z");

    @Autowired
    private LifecycleActionService service;

    @Autowired
    private LifecycleActionRepository repository;

    @Autowired
    private EciInstanceRepository instanceRepository;

    @Autowired
    private EciProperties properties;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void requestDeleteCreatesAndAdvancesOnePersistentActionWithoutStealingLease() {
        UUID instanceId = saveInstance(InstanceStatus.RUNNING);
        Instant firstDeadline = NOW.plusSeconds(300);

        requestDelete(instanceId, firstDeadline);

        LifecycleActionEntity created = repository.findByInstanceIdAndActionType(
                instanceId, LifecycleActionType.DELETE).orElseThrow();
        assertThat(created.getActionType()).isEqualTo(LifecycleActionType.DELETE);
        assertThat(created.getPhase()).isEqualTo(LifecycleActionPhase.DELETE_REQUEST);
        assertThat(created.getStatus()).isEqualTo(LifecycleActionStatus.READY);
        assertThat(created.getAttemptCount()).isZero();
        assertThat(created.getNextAttemptAt()).isEqualTo(firstDeadline);
        assertThat(created.getCreatedAt()).isEqualTo(CLOCK_NOW);
        assertThat(created.getUpdatedAt()).isEqualTo(CLOCK_NOW);
        assertThat(created.getLeaseToken()).isNull();
        assertThat(created.getLeaseUntil()).isNull();
        assertThat(created.getLastError()).isNull();
        assertThat(created.getCompletedAt()).isNull();
        assertThat(instanceRepository.findById(instanceId).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.STOPPING);

        Instant earlierDeadline = NOW.plusSeconds(120);
        requestDelete(instanceId, earlierDeadline);
        LifecycleActionEntity waiting = repository.findById(created.getId()).orElseThrow();
        assertThat(waiting.getNextAttemptAt()).isEqualTo(earlierDeadline);

        UUID leaseToken = UUID.randomUUID();
        waiting.setStatus(LifecycleActionStatus.IN_PROGRESS);
        waiting.setLeaseToken(leaseToken);
        waiting.setLeaseUntil(NOW.plusSeconds(30));
        waiting.setNextAttemptAt(NOW.plusSeconds(240));
        repository.saveAndFlush(waiting);
        requestDelete(instanceId, NOW.plusSeconds(180));

        LifecycleActionEntity leased = repository.findById(created.getId()).orElseThrow();
        assertThat(leased.getStatus()).isEqualTo(LifecycleActionStatus.IN_PROGRESS);
        assertThat(leased.getLeaseToken()).isEqualTo(leaseToken);
        assertThat(leased.getLeaseUntil()).isEqualTo(NOW.plusSeconds(30));
        assertThat(leased.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(180));

        leased.setStatus(LifecycleActionStatus.SUCCEEDED);
        leased.setCompletedAt(CLOCK_NOW);
        leased.setLeaseToken(null);
        leased.setLeaseUntil(null);
        repository.saveAndFlush(leased);
        instanceRepository.findById(instanceId).orElseThrow().setStatus(InstanceStatus.TERMINATED);
        instanceRepository.flush();
        requestDelete(instanceId, NOW);

        LifecycleActionEntity terminal = repository.findById(created.getId()).orElseThrow();
        assertThat(terminal.getStatus()).isEqualTo(LifecycleActionStatus.SUCCEEDED);
        assertThat(terminal.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(180));
        assertThat(terminal.getCompletedAt()).isEqualTo(CLOCK_NOW);
        assertThat(instanceRepository.findById(instanceId).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.TERMINATED);
    }

    @Test
    void completeTerminationRequiresCurrentLeaseAndCommitsActionAndInstanceTogether() {
        UUID instanceId = saveInstance(InstanceStatus.STOPPING);
        UUID actionId = save(instanceId, LifecycleActionType.DELETE,
                LifecycleActionPhase.WAIT_DELETED, LifecycleActionStatus.IN_PROGRESS,
                NOW, UUID.randomUUID(), NOW.plusSeconds(60));
        UUID currentLease = repository.findById(actionId).orElseThrow().getLeaseToken();

        completeTermination(instanceId, actionId, UUID.randomUUID());
        LifecycleActionEntity stillClaimed = repository.findById(actionId).orElseThrow();
        assertThat(stillClaimed.getStatus()).isEqualTo(LifecycleActionStatus.IN_PROGRESS);
        assertThat(instanceRepository.findById(instanceId).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.STOPPING);

        EciInstanceEntity instance = instanceRepository.findById(instanceId).orElseThrow();
        instance.setCloudStatus("Succeeded");
        instance.setFailureReason("old failure");
        instanceRepository.saveAndFlush(instance);
        completeTermination(instanceId, actionId, currentLease);

        LifecycleActionEntity completed = repository.findById(actionId).orElseThrow();
        EciInstanceEntity terminated = instanceRepository.findById(instanceId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(LifecycleActionStatus.SUCCEEDED);
        assertThat(completed.getLeaseToken()).isNull();
        assertThat(completed.getLeaseUntil()).isNull();
        assertThat(completed.getCompletedAt()).isEqualTo(CLOCK_NOW);
        assertThat(completed.getUpdatedAt()).isEqualTo(CLOCK_NOW);
        assertThat(terminated.getStatus()).isEqualTo(InstanceStatus.TERMINATED);
        assertThat(terminated.getCloudStatus()).isEqualTo("Succeeded");
        assertThat(terminated.getFailureReason()).isNull();
        assertThat(terminated.getUpdatedAt()).isEqualTo(CLOCK_NOW);
    }

    @Test
    void completeTasklessTerminationPersistsActionAndInstanceWithoutTaskService() {
        UUID instanceId = saveInstance(InstanceStatus.STOPPING);
        UUID leaseToken = UUID.randomUUID();
        UUID actionId = save(instanceId, LifecycleActionType.DELETE,
                LifecycleActionPhase.WAIT_DELETED, LifecycleActionStatus.IN_PROGRESS,
                NOW, leaseToken, NOW.plusSeconds(60));

        completeTermination(instanceId, actionId, leaseToken);

        LifecycleActionEntity completed = repository.findById(actionId).orElseThrow();
        EciInstanceEntity terminated = instanceRepository.findById(instanceId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(LifecycleActionStatus.SUCCEEDED);
        assertThat(completed.getLeaseToken()).isNull();
        assertThat(completed.getLeaseUntil()).isNull();
        assertThat(completed.getCompletedAt()).isEqualTo(CLOCK_NOW);
        assertThat(terminated.getTaskId()).isNull();
        assertThat(terminated.getStatus()).isEqualTo(InstanceStatus.TERMINATED);
        assertThat(terminated.getCloudStatus()).isEqualTo("Deleted");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void claimsDueActionOnlyOnceAndPersistsItsLease() throws Exception {
        UUID instanceId = UUID.randomUUID();
        UUID actionId = save(instanceId, LifecycleActionType.CREATE,
                LifecycleActionPhase.CREATE_REQUEST, LifecycleActionStatus.READY,
                NOW, null, null);

        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        requiresNew.setTimeout(5);
        CountDownLatch transactionsStarted = new CountDownLatch(2);
        CountDownLatch allowClaim = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Optional<ClaimedLifecycleAction>> firstFuture = executor.submit(
                    () -> claimAfterBarrier(requiresNew, actionId, transactionsStarted, allowClaim));
            Future<Optional<ClaimedLifecycleAction>> secondFuture = executor.submit(
                    () -> claimAfterBarrier(requiresNew, actionId, transactionsStarted, allowClaim));

            assertThat(transactionsStarted.await(5, TimeUnit.SECONDS)).isTrue();
            allowClaim.countDown();
            Optional<ClaimedLifecycleAction> first = firstFuture.get(5, TimeUnit.SECONDS);
            Optional<ClaimedLifecycleAction> second = secondFuture.get(5, TimeUnit.SECONDS);

            assertThat(first.isPresent()).isNotEqualTo(second.isPresent());
            ClaimedLifecycleAction claimed = first.orElseGet(second::orElseThrow);
            assertThat(claimed.actionId()).isEqualTo(actionId);
            assertThat(claimed.instanceId()).isEqualTo(instanceId);
            assertThat(claimed.actionType()).isEqualTo(LifecycleActionType.CREATE);
            assertThat(claimed.phase()).isEqualTo(LifecycleActionPhase.CREATE_REQUEST);
            assertThat(claimed.leaseToken()).isNotNull();

            LifecycleActionEntity stored = repository.findById(actionId).orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(LifecycleActionStatus.IN_PROGRESS);
            assertThat(stored.getLeaseToken()).isEqualTo(claimed.leaseToken());
            assertThat(stored.getLeaseUntil()).isEqualTo(NOW.plusSeconds(
                    properties.getLifecycleAction().getLeaseSec()));
        } finally {
            allowClaim.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        repository.deleteById(actionId);
    }

    @Test
    void oldLeaseCannotReleaseNewClaimButCurrentLeaseCanBeReleased() {
        UUID actionId = save(UUID.randomUUID(), LifecycleActionType.DELETE,
                LifecycleActionPhase.DELETE_REQUEST, LifecycleActionStatus.READY,
                NOW, null, null);
        UUID oldLeaseToken = service.claim(actionId, NOW).orElseThrow().leaseToken();

        LifecycleActionEntity expiredLease = repository.findById(actionId).orElseThrow();
        expiredLease.setLeaseUntil(NOW);
        repository.saveAndFlush(expiredLease);

        UUID newLeaseToken = service.claim(actionId, NOW).orElseThrow().leaseToken();
        assertThat(newLeaseToken).isNotEqualTo(oldLeaseToken);

        Instant retryAt = NOW.plusSeconds(1);
        service.releaseAfterDispatchFailure(actionId, oldLeaseToken, retryAt, "stale rejection");

        LifecycleActionEntity stillClaimed = repository.findById(actionId).orElseThrow();
        assertThat(stillClaimed.getStatus()).isEqualTo(LifecycleActionStatus.IN_PROGRESS);
        assertThat(stillClaimed.getLeaseToken()).isEqualTo(newLeaseToken);

        service.releaseAfterDispatchFailure(actionId, newLeaseToken, retryAt, "executor rejected");

        LifecycleActionEntity released = repository.findById(actionId).orElseThrow();
        assertThat(released.getStatus()).isEqualTo(LifecycleActionStatus.WAITING);
        assertThat(released.getLeaseToken()).isNull();
        assertThat(released.getLeaseUntil()).isNull();
        assertThat(released.getNextAttemptAt()).isEqualTo(retryAt);
        assertThat(released.getLastError()).isEqualTo("executor rejected");
        assertThat(released.getUpdatedAt()).isEqualTo(CLOCK_NOW);
    }

    @Test
    void recoversOnlyExpiredLeases() {
        UUID expiredId = save(UUID.randomUUID(), LifecycleActionType.CREATE,
                LifecycleActionPhase.CREATE_REQUEST, LifecycleActionStatus.IN_PROGRESS,
                NOW.minusSeconds(10), UUID.randomUUID(), NOW.minusSeconds(1));
        UUID activeId = save(UUID.randomUUID(), LifecycleActionType.DELETE,
                LifecycleActionPhase.DELETE_REQUEST, LifecycleActionStatus.IN_PROGRESS,
                NOW.minusSeconds(10), UUID.randomUUID(), NOW.plusSeconds(1));
        UUID readyId = save(UUID.randomUUID(), LifecycleActionType.CREATE,
                LifecycleActionPhase.CREATE_REQUEST, LifecycleActionStatus.READY,
                NOW.minusSeconds(5), null, null);

        assertThat(service.recoverExpiredLeases(NOW)).isEqualTo(1);

        LifecycleActionEntity expired = repository.findById(expiredId).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(LifecycleActionStatus.WAITING);
        assertThat(expired.getLeaseToken()).isNull();
        assertThat(expired.getLeaseUntil()).isNull();
        assertThat(expired.getNextAttemptAt()).isEqualTo(NOW.minusSeconds(10));
        assertThat(expired.getUpdatedAt()).isEqualTo(NOW);

        LifecycleActionEntity active = repository.findById(activeId).orElseThrow();
        assertThat(active.getStatus()).isEqualTo(LifecycleActionStatus.IN_PROGRESS);
        assertThat(active.getLeaseToken()).isNotNull();
        assertThat(repository.findById(readyId).orElseThrow().getStatus())
                .isEqualTo(LifecycleActionStatus.READY);
    }

    private UUID save(
            UUID instanceId,
            LifecycleActionType type,
            LifecycleActionPhase phase,
            LifecycleActionStatus status,
            Instant nextAttemptAt,
            UUID leaseToken,
            Instant leaseUntil) {
        UUID id = UUID.randomUUID();
        LifecycleActionEntity action = new LifecycleActionEntity();
        action.setId(id);
        action.setInstanceId(instanceId);
        action.setActionType(type);
        action.setPhase(phase);
        action.setStatus(status);
        action.setAttemptCount(0);
        action.setNextAttemptAt(nextAttemptAt);
        action.setLeaseToken(leaseToken);
        action.setLeaseUntil(leaseUntil);
        action.setCreatedAt(NOW);
        action.setUpdatedAt(NOW);
        repository.saveAndFlush(action);
        return id;
    }

    private UUID saveInstance(InstanceStatus status) {
        UUID id = UUID.randomUUID();
        EciInstanceEntity instance = new EciInstanceEntity();
        instance.setId(id);
        instance.setName("delete-test");
        instance.setImage("nginx:alpine");
        instance.setCpu(1);
        instance.setMemory(2);
        instance.setStatus(status);
        instance.setCloudInstanceId("eci-delete-test");
        instance.setEnvJson("{}");
        instance.setCreatedAt(NOW);
        instance.setUpdatedAt(NOW);
        instanceRepository.saveAndFlush(instance);
        return id;
    }

    private void requestDelete(UUID instanceId, Instant notBefore) {
        try {
            service.getClass().getMethod("requestDelete", UUID.class, Instant.class)
                    .invoke(service, instanceId, notBefore);
        } catch (NoSuchMethodException exception) {
            fail("LifecycleActionService.requestDelete(UUID, Instant) is required", exception);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("requestDelete invocation failed", exception);
        }
    }

    private void completeTermination(UUID instanceId, UUID actionId, UUID leaseToken) {
        try {
            service.getClass().getMethod("completeInstanceTermination",
                            UUID.class, UUID.class, UUID.class)
                    .invoke(service, instanceId, actionId, leaseToken);
        } catch (NoSuchMethodException exception) {
            fail("LifecycleActionService.completeInstanceTermination is required", exception);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("completeInstanceTermination invocation failed", exception);
        }
    }

    private Optional<ClaimedLifecycleAction> claimAfterBarrier(
            TransactionTemplate transaction,
            UUID actionId,
            CountDownLatch transactionsStarted,
            CountDownLatch allowClaim) {
        return transaction.execute(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            transactionsStarted.countDown();
            try {
                if (!allowClaim.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("claim barrier timed out");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("claim barrier interrupted", exception);
            }
            return service.claim(actionId, NOW);
        });
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedLifecycleClock() {
            return Clock.fixed(CLOCK_NOW, ZoneOffset.UTC);
        }
    }
}
