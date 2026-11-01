package com.codestep.submission;

import com.codestep.judge.SubmissionResult;

import java.util.UUID;

/** Status and verdict updates on their way to the browsers watching a submission over SSE. */
public interface SubmissionEvents {

    void publish(UUID id, SubmissionStatus status, SubmissionResult result);

    record Event(UUID id, SubmissionStatus status, SubmissionResult result) {
    }
}
