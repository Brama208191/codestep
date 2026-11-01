package com.codestep.submission;

import com.codestep.judge.SubmissionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

class SseHubTest {

    private final SseHub hub = new SseHub(new ObjectMapper());

    @Test
    void aVerdictArrivingTwiceOnTheSameStreamIsHarmless() {
        UUID id = UUID.randomUUID();
        SseEmitter emitter = hub.subscribe(id);
        SubmissionResult result = SubmissionResult.internalError("x");

        // The judge's event and the controller's own lookup race for a stream opened just as judging ends.
        hub.deliver(new SubmissionEvents.Event(id, SubmissionStatus.DONE, result));

        assertThatCode(() -> hub.sendResult(emitter, result)).doesNotThrowAnyException();
        assertThatCode(() -> hub.sendStatus(emitter, SubmissionStatus.DONE)).doesNotThrowAnyException();
    }
}
