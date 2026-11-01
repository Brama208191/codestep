package com.codestep.submission;

import java.util.UUID;

/** Hands a stored submission to the judges: a Redis Stream, or an in-process queue in single-container mode. */
public interface SubmissionDispatcher {

    void enqueue(UUID submissionId);
}
