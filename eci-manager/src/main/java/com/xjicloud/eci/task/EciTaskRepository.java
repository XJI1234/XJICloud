package com.xjicloud.eci.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** 任务本地记录的 Spring Data 仓储。 */
public interface EciTaskRepository extends JpaRepository<EciTaskEntity, UUID> {

    List<EciTaskEntity> findByStatusOrderByCreatedAtDesc(TaskStatus status);

    List<EciTaskEntity> findAllByOrderByCreatedAtDesc();

    /** 启动恢复 / 终态轮询：按状态集合拉取非终态任务。 */
    List<EciTaskEntity> findByStatusIn(Collection<TaskStatus> statuses);
}
