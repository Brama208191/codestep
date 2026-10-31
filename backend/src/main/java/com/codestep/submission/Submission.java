package com.codestep.submission;

import com.codestep.judge.SubmissionResult;

import java.time.Instant;
import java.util.UUID;

public record Submission(
        UUID id,
        String clientId,
        String problemSlug,
        String code,
        boolean samplesOnly,
        SubmissionStatus status,
        SubmissionResult result,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {
}
