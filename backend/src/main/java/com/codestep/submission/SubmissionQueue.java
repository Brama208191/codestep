package com.codestep.submission;

import jakarta.annotation.PostConstruct;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class SubmissionQueue {

    static final String STREAM = "codestep:submissions";
    static final String GROUP = "judges";
    private static final long MAX_LENGTH = 10_000;

    private final StringRedisTemplate redis;

    public SubmissionQueue(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @PostConstruct
    void createGroup() {
        try {
            redis.execute((RedisCallback<Object>) connection -> createGroup(connection));
        } catch (DataAccessException e) {
            if (!isBusyGroup(e)) {
                throw e;
            }
        }
    }

    private static boolean isBusyGroup(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (String.valueOf(t.getMessage()).contains("BUSYGROUP")) {
                return true;
            }
        }
        return false;
    }

    private static Object createGroup(RedisConnection connection) {
        return connection.execute("XGROUP", bytes("CREATE"), bytes(STREAM), bytes(GROUP), bytes("0"), bytes("MKSTREAM"));
    }

    public void enqueue(UUID submissionId) {
        redis.opsForStream().add(StreamRecords.string(Map.of("id", submissionId.toString())).withStreamKey(STREAM));
        redis.opsForStream().trim(STREAM, MAX_LENGTH, true);
    }

    public List<MapRecord<String, Object, Object>> next(String consumer, Duration block) {
        List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                Consumer.from(GROUP, consumer),
                StreamReadOptions.empty().count(1).block(block),
                StreamOffset.create(STREAM, ReadOffset.lastConsumed()));
        return records == null ? List.of() : records;
    }

    public List<MapRecord<String, Object, Object>> claimAbandoned(String consumer, Duration minIdle) {
        List<RecordId> abandoned = redis.opsForStream()
                .pending(STREAM, GROUP, Range.unbounded(), 10)
                .stream()
                .filter(message -> message.getElapsedTimeSinceLastDelivery().compareTo(minIdle) > 0)
                .map(PendingMessage::getId)
                .toList();
        if (abandoned.isEmpty()) {
            return List.of();
        }
        return redis.opsForStream().claim(STREAM, GROUP, consumer, minIdle, abandoned.toArray(RecordId[]::new));
    }

    public void acknowledge(RecordId recordId) {
        redis.opsForStream().acknowledge(STREAM, GROUP, recordId);
    }

    public long pending() {
        return redis.opsForStream().pending(STREAM, GROUP).getTotalPendingMessages();
    }

    static UUID submissionId(MapRecord<String, Object, Object> record) {
        return UUID.fromString(String.valueOf(record.getValue().get("id")));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
