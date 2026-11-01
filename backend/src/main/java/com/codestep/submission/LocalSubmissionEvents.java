package com.codestep.submission;

import com.codestep.judge.SubmissionResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Single-container mode: the worker and the SSE streams live in the same process. */
@Component
@ConditionalOnProperty(name = "codestep.queue", havingValue = "memory")
class LocalSubmissionEvents implements SubmissionEvents {

    private final ObjectProvider<SseHub> hub;

    LocalSubmissionEvents(ObjectProvider<SseHub> hub) {
        this.hub = hub;
    }

    @Override
    public void publish(UUID id, SubmissionStatus status, SubmissionResult result) {
        hub.ifAvailable(h -> h.deliver(new Event(id, status, result)));
    }
}
