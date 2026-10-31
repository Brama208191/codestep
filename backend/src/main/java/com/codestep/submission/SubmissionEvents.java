package com.codestep.submission;

import com.codestep.judge.SubmissionResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SubmissionEvents {

    static final String CHANNEL = "codestep:submission-events";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public SubmissionEvents(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public void publish(UUID id, SubmissionStatus status, SubmissionResult result) {
        try {
            redis.convertAndSend(CHANNEL, mapper.writeValueAsString(new Event(id, status, result)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public record Event(UUID id, SubmissionStatus status, SubmissionResult result) {
    }
}
