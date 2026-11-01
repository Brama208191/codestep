package com.codestep.submission;

import com.codestep.judge.SubmissionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "codestep.api.enabled", havingValue = "true", matchIfMissing = true)
public class SseHub implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(SseHub.class);
    private static final long TIMEOUT_MILLIS = Duration.ofMinutes(3).toMillis();

    private final Map<UUID, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;

    public SseHub(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public SseEmitter subscribe(UUID id) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        emitters.computeIfAbsent(id, key -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(id, emitter));
        emitter.onTimeout(() -> remove(id, emitter));
        emitter.onError(error -> remove(id, emitter));
        return emitter;
    }

    // The verdict can reach a fresh stream twice: from the controller, which found the submission
    // already done, and from the judge's event at the same moment. Whichever comes second finds the
    // stream completed (IllegalStateException) and has nothing left to do.

    public void sendStatus(SseEmitter emitter, SubmissionStatus status) {
        try {
            emitter.send(SseEmitter.event().name("status").data(Map.of("status", status), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            emitter.completeWithError(e);
        } catch (IllegalStateException alreadyCompleted) {
            // the result was sent first
        }
    }

    public void sendResult(SseEmitter emitter, SubmissionResult result) {
        try {
            emitter.send(SseEmitter.event().name("result").data(result, MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        } catch (IllegalStateException alreadyCompleted) {
            // the result was already sent on this stream
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        SubmissionEvents.Event event;
        try {
            event = mapper.readValue(message.getBody(), SubmissionEvents.Event.class);
        } catch (IOException e) {
            log.warn("Ignoring malformed submission event", e);
            return;
        }
        deliver(event);
    }

    /** Sends the event to every browser watching that submission. */
    public void deliver(SubmissionEvents.Event event) {
        Set<SseEmitter> listeners = emitters.get(event.id());
        if (listeners == null) {
            return;
        }
        for (SseEmitter emitter : listeners) {
            if (event.result() != null) {
                sendResult(emitter, event.result());
            } else {
                sendStatus(emitter, event.status());
            }
        }
    }

    private void remove(UUID id, SseEmitter emitter) {
        emitters.computeIfPresent(id, (key, set) -> {
            set.remove(emitter);
            return set.isEmpty() ? null : set;
        });
    }

    @Configuration
    @ConditionalOnExpression("${codestep.api.enabled:true} and '${codestep.queue:redis}' == 'redis'")
    static class ListenerConfig {

        @Bean
        RedisMessageListenerContainer submissionEventsListener(RedisConnectionFactory factory, SseHub hub) {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(factory);
            container.addMessageListener(hub, new ChannelTopic(RedisSubmissionEvents.CHANNEL));
            return container;
        }
    }
}
