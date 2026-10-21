package com.xjicloud.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.job.JobStatus;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class JobProgressSseService {

    private final ObjectMapper objectMapper;
    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public JobProgressSseService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter subscribe(UUID jobId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(jobId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(jobId, emitter));
        emitter.onTimeout(() -> removeEmitter(jobId, emitter));
        emitter.onError(ex -> removeEmitter(jobId, emitter));
        return emitter;
    }

    public void publish(UUID jobId, JobStatus status, int progress, String stage, String message) {
        publish(jobId, status, progress, stage, message, null, null, null, false, false, null);
    }

    public void publish(
            UUID jobId,
            JobStatus status,
            int progress,
            String stage,
            String message,
            String computeStatus,
            String cloudStatus,
            String computeStatusReason,
            boolean cancellationPending,
            boolean cleanupPending,
            Instant updatedAt
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jobId", jobId.toString());
        payload.put("status", status.name());
        payload.put("progress", progress);
        payload.put("stage", stage != null ? stage : "");
        payload.put("message", message != null ? message : "");
        payload.put("computeStatus", computeStatus);
        payload.put("cloudStatus", cloudStatus);
        payload.put("computeStatusReason", computeStatusReason);
        payload.put("cancellationPending", cancellationPending);
        payload.put("cleanupPending", cleanupPending);
        payload.put("updatedAt", updatedAt);
        publishPayload(jobId, payload);
    }

    private void publishPayload(UUID jobId, Map<String, Object> payload) {
        CopyOnWriteArrayList<SseEmitter> jobEmitters = emitters.get(jobId);
        if (jobEmitters == null) {
            return;
        }
        for (SseEmitter emitter : jobEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("progress")
                        .data(toJson(payload)));
            } catch (IOException ex) {
                removeEmitter(jobId, emitter);
            }
        }
    }

    private void removeEmitter(UUID jobId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> jobEmitters = emitters.get(jobId);
        if (jobEmitters != null) {
            jobEmitters.remove(emitter);
            if (jobEmitters.isEmpty()) {
                emitters.remove(jobId);
            }
        }
        emitter.complete();
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

}
