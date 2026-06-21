package com.xjicloud.queue;

import com.xjicloud.config.RedisQueueProperties;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisQueueService {

    private final StringRedisTemplate redisTemplate;
    private final RedisQueueProperties queueProperties;

    public RedisQueueService(StringRedisTemplate redisTemplate, RedisQueueProperties queueProperties) {
        this.redisTemplate = redisTemplate;
        this.queueProperties = queueProperties;
    }

    public void enqueue(UUID jobId) {
        redisTemplate.opsForList().rightPush(queueProperties.queueKey(), jobId.toString());
    }

    public UUID dequeueBlocking(long timeoutSeconds) {
        String value = redisTemplate.opsForList().leftPop(queueProperties.queueKey(), java.time.Duration.ofSeconds(timeoutSeconds));
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }

    public long queueDepth() {
        Long size = redisTemplate.opsForList().size(queueProperties.queueKey());
        return size != null ? size : 0L;
    }

    public void remove(UUID jobId) {
        redisTemplate.opsForList().remove(queueProperties.queueKey(), 0, jobId.toString());
    }
}
