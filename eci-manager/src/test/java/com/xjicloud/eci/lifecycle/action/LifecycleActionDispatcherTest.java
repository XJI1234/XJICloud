package com.xjicloud.eci.lifecycle.action;

import com.xjicloud.eci.config.EciProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LifecycleActionDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");

    @Mock
    private LifecycleActionRepository repository;
    @Mock
    private LifecycleActionService actionService;
    @Mock
    private LifecycleActionProcessor processor;
    @Mock
    private TaskExecutor executor;
    @Mock
    private Clock clock;

    private EciProperties properties;
    private LifecycleActionDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        properties = new EciProperties();
        properties.getLifecycleAction().setBatchSize(2);
        when(clock.instant()).thenReturn(NOW);
        dispatcher = new LifecycleActionDispatcher(
                repository, actionService, processor, executor, properties, clock);
    }

    @Test
    void scansBoundedCreateAndDeleteBatchAndSubmitsClaimedSteps() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID firstInstance = UUID.randomUUID();
        UUID firstLease = UUID.randomUUID();
        UUID secondInstance = UUID.randomUUID();
        UUID secondLease = UUID.randomUUID();
        when(repository.findDueIds(eq(NOW), eq(Set.of(LifecycleActionType.CREATE, LifecycleActionType.DELETE)),
                eq(Set.of(LifecycleActionStatus.READY, LifecycleActionStatus.WAITING)),
                eq(LifecycleActionStatus.IN_PROGRESS), any(Pageable.class)))
                .thenReturn(List.of(firstId, secondId));
        when(actionService.claim(firstId, NOW)).thenReturn(Optional.of(new ClaimedLifecycleAction(
                firstId, firstInstance, LifecycleActionType.CREATE,
                LifecycleActionPhase.CREATE_REQUEST, firstLease)));
        when(actionService.claim(secondId, NOW)).thenReturn(Optional.of(new ClaimedLifecycleAction(
                secondId, secondInstance, LifecycleActionType.DELETE,
                LifecycleActionPhase.WAIT_DELETED, secondLease)));
        ArgumentCaptor<Runnable> submitted = ArgumentCaptor.forClass(Runnable.class);
        doNothing().when(executor).execute(any(Runnable.class));

        dispatcher.dispatchOnce();
        verify(executor, org.mockito.Mockito.times(2)).execute(submitted.capture());
        submitted.getAllValues().forEach(Runnable::run);

        verify(repository).findDueIds(eq(NOW), eq(Set.of(LifecycleActionType.CREATE, LifecycleActionType.DELETE)),
                eq(Set.of(LifecycleActionStatus.READY, LifecycleActionStatus.WAITING)),
                eq(LifecycleActionStatus.IN_PROGRESS), any(Pageable.class));
        verify(processor).process(firstId, firstLease);
        verify(processor).process(secondId, secondLease);
    }

    @Test
    void rejectedSubmissionReleasesCurrentLeaseAndContinuesOtherActions() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID firstInstance = UUID.randomUUID();
        UUID firstLease = UUID.randomUUID();
        UUID secondInstance = UUID.randomUUID();
        UUID secondLease = UUID.randomUUID();
        when(repository.findDueIds(eq(NOW), eq(Set.of(LifecycleActionType.CREATE, LifecycleActionType.DELETE)),
                eq(Set.of(LifecycleActionStatus.READY, LifecycleActionStatus.WAITING)),
                eq(LifecycleActionStatus.IN_PROGRESS), any(Pageable.class)))
                .thenReturn(List.of(firstId, secondId));
        when(actionService.claim(firstId, NOW)).thenReturn(Optional.of(new ClaimedLifecycleAction(
                firstId, firstInstance, LifecycleActionType.DELETE,
                LifecycleActionPhase.DELETE_REQUEST, firstLease)));
        when(actionService.claim(secondId, NOW)).thenReturn(Optional.of(new ClaimedLifecycleAction(
                secondId, secondInstance, LifecycleActionType.DELETE,
                LifecycleActionPhase.DELETE_REQUEST, secondLease)));
        doThrow(new RejectedExecutionException("queue full"))
                .doNothing().when(executor).execute(any(Runnable.class));

        dispatcher.dispatchOnce();

        verify(actionService).releaseAfterDispatchFailure(firstId, firstLease,
                NOW.plusSeconds(1), "executor rejected lifecycle action dispatch");
        verify(actionService, never()).releaseAfterDispatchFailure(eq(secondId), any(), any(), any());
        verify(actionService).claim(secondId, NOW);
        verify(executor, org.mockito.Mockito.times(2)).execute(any(Runnable.class));
    }

    @Test
    void claimFailureForOneActionDoesNotStopBatch() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID lease = UUID.randomUUID();
        when(repository.findDueIds(eq(NOW), eq(Set.of(LifecycleActionType.CREATE, LifecycleActionType.DELETE)),
                eq(Set.of(LifecycleActionStatus.READY, LifecycleActionStatus.WAITING)),
                eq(LifecycleActionStatus.IN_PROGRESS), any(Pageable.class)))
                .thenReturn(List.of(firstId, secondId));
        when(actionService.claim(firstId, NOW)).thenThrow(new IllegalStateException("database hiccup"));
        when(actionService.claim(secondId, NOW)).thenReturn(Optional.of(new ClaimedLifecycleAction(
                secondId, UUID.randomUUID(), LifecycleActionType.DELETE,
                LifecycleActionPhase.DELETE_REQUEST, lease)));

        dispatcher.dispatchOnce();

        verify(actionService).claim(secondId, NOW);
        verify(executor).execute(any(Runnable.class));
    }
}
