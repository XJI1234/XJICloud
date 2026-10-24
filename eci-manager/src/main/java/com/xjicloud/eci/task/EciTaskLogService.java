package com.xjicloud.eci.task;

import com.xjicloud.eci.common.BusinessException;
import com.xjicloud.eci.instance.EciInstanceService;
import com.xjicloud.eci.instance.dto.InstanceResponse;
import com.xjicloud.eci.provider.EciProvider;
import com.xjicloud.eci.task.dto.TaskLogResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.EnumSet;
import java.util.UUID;

/**
 * 操作台只读日志尾部。不改变失败删实例顺序；拉日志失败返回可读字符串。
 */
@Service
public class EciTaskLogService {

    private static final int MAX_TAIL_LINES = 10_000;
    private static final EnumSet<TaskStatus> TERMINAL =
            EnumSet.of(TaskStatus.SUCCEEDED, TaskStatus.FAILED, TaskStatus.CANCELLED);

    private final EciTaskRepository taskRepository;
    private final EciInstanceService instanceService;
    private final EciProvider eciProvider;

    public EciTaskLogService(
            EciTaskRepository taskRepository,
            EciInstanceService instanceService,
            EciProvider eciProvider) {
        this.taskRepository = taskRepository;
        this.instanceService = instanceService;
        this.eciProvider = eciProvider;
    }

    @Transactional(readOnly = true)
    public TaskLogResponse logs(UUID id, int tailLines) {
        if (tailLines <= 0) {
            throw new BusinessException("tailLines 须为正整数");
        }
        int lines = Math.min(tailLines, MAX_TAIL_LINES);
        EciTaskEntity task = taskRepository.findById(id)
                .orElseThrow(() -> new BusinessException("任务不存在: " + id, HttpStatus.NOT_FOUND));

        if (TERMINAL.contains(task.getStatus())) {
            String content = task.getContainerLogTail() == null ? "" : task.getContainerLogTail();
            String note = null;
            if (task.getStatus() == TaskStatus.SUCCEEDED && !StringUtils.hasText(content)) {
                note = "成功任务不保留全量日志";
            }
            return new TaskLogResponse(task.getId(), task.getStatus(), "PERSISTED", content, lines, note);
        }

        if (task.getInstanceId() == null) {
            return empty(task, lines, "尚无云实例，日志为空");
        }

        InstanceResponse instance;
        try {
            instance = instanceService.get(task.getInstanceId());
        } catch (BusinessException ex) {
            if (ex.getStatus() == HttpStatus.NOT_FOUND) {
                return empty(task, lines, "关联实例不存在，日志为空");
            }
            throw ex;
        }

        if (!StringUtils.hasText(instance.cloudInstanceId())) {
            return empty(task, lines, "尚无云实例，日志为空");
        }

        String content;
        try {
            String fetched = eciProvider.fetchContainerLog(
                    instance.cloudInstanceId(), lines, instanceService.getRegionId(instance.id()));
            content = fetched == null ? "" : fetched;
        } catch (Exception ex) {
            content = "fetchContainerLog failed: "
                    + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        }
        return new TaskLogResponse(task.getId(), task.getStatus(), "LIVE", content, lines, null);
    }

    private static TaskLogResponse empty(EciTaskEntity task, int lines, String note) {
        return new TaskLogResponse(task.getId(), task.getStatus(), "EMPTY", "", lines, note);
    }
}
