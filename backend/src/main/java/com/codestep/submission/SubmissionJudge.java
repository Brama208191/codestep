package com.codestep.submission;

import com.codestep.judge.JudgeService;
import com.codestep.judge.SubmissionResult;
import com.codestep.problem.Problem;
import com.codestep.problem.ProblemRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Judges one submission end to end. Shared by the Redis worker and the in-process worker. */
@Component
class SubmissionJudge {

    private final SubmissionRepository submissions;
    private final SubmissionEvents events;
    private final ProblemRepository problems;
    private final JudgeService judge;
    private final MeterRegistry metrics;

    SubmissionJudge(SubmissionRepository submissions,
                    SubmissionEvents events,
                    ProblemRepository problems,
                    JudgeService judge,
                    MeterRegistry metrics) {
        this.submissions = submissions;
        this.events = events;
        this.problems = problems;
        this.judge = judge;
        this.metrics = metrics;
    }

    /**
     * Claims the submission, judges it, stores and publishes the verdict. Does nothing if another
     * judge holds a claim younger than {@code staleAfter}, or the submission is already done.
     */
    void judge(UUID id, Duration staleAfter) {
        Submission submission = submissions.findById(id).orElse(null);
        if (submission == null || !submissions.claimForJudging(id, staleAfter)) {
            return;
        }
        events.publish(id, SubmissionStatus.RUNNING, null);
        metrics.timer("codestep.queue.wait").record(Duration.between(submission.createdAt(), Instant.now()));

        Problem problem = problems.findBySlug(submission.problemSlug()).orElse(null);
        Timer.Sample sample = Timer.start(metrics);
        SubmissionResult result = problem == null
                ? SubmissionResult.internalError("This problem no longer exists.")
                : judge.judge(problem, submission.code(), submission.samplesOnly());
        sample.stop(metrics.timer("codestep.judge.duration", "verdict", result.verdict().name()));

        submissions.complete(id, result);
        events.publish(id, SubmissionStatus.DONE, result);
    }
}
