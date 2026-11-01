package com.codestep.submission;

import com.codestep.judge.SubmissionResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Redis pub/sub, so a worker on any machine reaches the API instance holding the browser's SSE stream. */
@Component
@ConditionalOnProperty(name = "codestep.queue", havingValue = "redis", matchIfMissing = true)
class RedisSubmissionEvents implements SubmissionEvents {

    static final String CHANNEL = "codestep:submission-events";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    RedisSubmissionEvents(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    @Override
    public void publish(UUID id, SubmissionStatus status, SubmissionResult result) {
        try {
            redis.convertAndSend(CHANNEL, mapper.writeValueAsString(new Event(id, status, result)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
