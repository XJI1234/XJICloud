package com.xjicloud.eci.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** 任务本地记录的 Spring Data 仓储。 */
public interface EciTaskRepository extends JpaRepository<EciTaskEntity, UUID> {

    List<EciTaskEntity> findByStatusOrderByCreatedAtDesc(TaskStatus status);

    List<EciTaskEntity> findAllByOrderByCreatedAtDesc();

    /** 启动恢复 / 终态轮询：按状态集合拉取非终态任务。 */
    List<EciTaskEntity> findByStatusIn(Collection<TaskStatus> statuses);

    /**
     * 仅非终态可标 FAILED，避免与 cancel 竞态时覆盖 CANCELLED。
     * @return 更新行数（0 表示已终态或任务不存在）
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE EciTaskEntity t
            SET t.status = :failed,
                t.failureReason = :reason,
                t.containerLogTail = :logTail,
                t.endedAt = :now,
                t.updatedAt = :now
            WHERE t.id = :id
              AND t.status IN :active
            """)
    int markFailedIfActive(
            @Param("id") UUID id,
            @Param("failed") TaskStatus failed,
            @Param("reason") String reason,
            @Param("logTail") String logTail,
            @Param("now") Instant now,
            @Param("active") Collection<TaskStatus> active);

    /**
     * 仅非终态可标 SUCCEEDED，避免覆盖 CANCELLED/FAILED。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE EciTaskEntity t
            SET t.status = :succeeded,
                t.failureReason = null,
                t.endedAt = :now,
                t.updatedAt = :now
            WHERE t.id = :id
              AND t.status IN :active
            """)
    int markSucceededIfActive(
            @Param("id") UUID id,
            @Param("succeeded") TaskStatus succeeded,
            @Param("now") Instant now,
            @Param("active") Collection<TaskStatus> active);

    /**
     * 仅非终态可标 CANCELLED，避免覆盖 SUCCEEDED/FAILED。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE EciTaskEntity t
            SET t.status = :cancelled,
                t.endedAt = :now,
                t.updatedAt = :now
            WHERE t.id = :id
              AND t.status IN :active
            """)
    int markCancelledIfActive(
            @Param("id") UUID id,
            @Param("cancelled") TaskStatus cancelled,
            @Param("now") Instant now,
            @Param("active") Collection<TaskStatus> active);
}
