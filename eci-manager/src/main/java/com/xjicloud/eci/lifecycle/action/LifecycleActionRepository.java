package com.xjicloud.eci.lifecycle.action;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LifecycleActionRepository extends JpaRepository<LifecycleActionEntity, UUID> {

    @Query("""
            select a.id from LifecycleActionEntity a
            where a.actionType in :enabledTypes
              and ((a.status in :ready and a.nextAttemptAt <= :now)
                or (a.status = :inProgress and a.leaseUntil <= :now))
            order by a.nextAttemptAt asc, a.createdAt asc
            """)
    List<UUID> findDueIds(
            @Param("now") Instant now,
            @Param("enabledTypes") Collection<LifecycleActionType> enabledTypes,
            @Param("ready") Collection<LifecycleActionStatus> ready,
            @Param("inProgress") LifecycleActionStatus inProgress,
            Pageable pageable);

    Optional<LifecycleActionEntity> findByInstanceIdAndActionType(
            UUID instanceId,
            LifecycleActionType actionType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select action from LifecycleActionEntity action "
            + "where action.instanceId = :instanceId and action.actionType = :actionType")
    Optional<LifecycleActionEntity> findForUpdateByInstanceIdAndActionType(
            @Param("instanceId") UUID instanceId,
            @Param("actionType") LifecycleActionType actionType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select action from LifecycleActionEntity action where action.id = :id")
    Optional<LifecycleActionEntity> findForUpdateById(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update LifecycleActionEntity a
            set a.leaseToken = :leaseToken,
                a.leaseUntil = :leaseUntil,
                a.status = :inProgress,
                a.updatedAt = :now
            where a.id = :id
              and ((a.status in :ready and a.nextAttemptAt <= :now)
                or (a.status = :inProgress and a.leaseUntil <= :now))
            """)
    int claim(
            @Param("id") UUID id,
            @Param("leaseToken") UUID leaseToken,
            @Param("now") Instant now,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("ready") Collection<LifecycleActionStatus> ready,
            @Param("inProgress") LifecycleActionStatus inProgress);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update LifecycleActionEntity a
            set a.status = :waiting,
                a.leaseToken = null,
                a.leaseUntil = null,
                a.nextAttemptAt = :nextAttemptAt,
                a.updatedAt = :now
            where a.id = :id
              and a.leaseToken = :leaseToken
              and a.status = :inProgress
            """)
    int releaseExpiredLeases(
            @Param("id") UUID id,
            @Param("leaseToken") UUID leaseToken,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("now") Instant now,
            @Param("waiting") LifecycleActionStatus waiting,
            @Param("inProgress") LifecycleActionStatus inProgress);
}
