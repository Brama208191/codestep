package com.codestep.submission;

import com.codestep.judge.JudgeService;
import com.codestep.judge.SubmissionResult;
import com.codestep.problem.Problem;
import com.codestep.problem.ProblemRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "codestep.worker.enabled", havingValue = "true", matchIfMissing = true)
public class JudgeWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(JudgeWorker.class);
    private static final Duration BLOCK = Duration.ofSeconds(5);
    private static final Duration ABANDONED_AFTER = Duration.ofMinutes(2);
    private static final Duration SWEEP_EVERY = Duration.ofSeconds(30);

    private final SubmissionQueue queue;
    private final SubmissionRepository submissions;
    private final SubmissionEvents events;
    private final ProblemRepository problems;
    private final JudgeService judge;
    private final MeterRegistry metrics;
    private final int threadCount;
    private final List<Thread> threads = new ArrayList<>();
    private volatile boolean running;

    public JudgeWorker(SubmissionQueue queue,
                       SubmissionRepository submissions,
                       SubmissionEvents events,
                       ProblemRepository problems,
                       JudgeService judge,
                       MeterRegistry metrics,
                       @Value("${codestep.worker.threads}") int threadCount) {
        this.queue = queue;
        this.submissions = submissions;
        this.events = events;
        this.problems = problems;
        this.judge = judge;
        this.metrics = metrics;
        this.threadCount = threadCount;
    }

    @Override
    public void start() {
        running = true;
        String host = hostName();
        for (int i = 0; i < threadCount; i++) {
            String consumer = host + "-" + i;
            Thread thread = Thread.ofPlatform().name("judge-" + i).daemon(true).start(() -> loop(consumer, consumer.endsWith("-0")));
            threads.add(thread);
        }
        log.info("Judge worker started with {} thread(s)", threadCount);
    }

    @Override
    public void stop() {
        running = false;
        for (Thread thread : threads) {
            try {
                thread.join(TimeUnit.SECONDS.toMillis(30));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        threads.clear();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void loop(String consumer, boolean sweeper) {
        long nextSweep = 0;
        while (running) {
            try {
                if (sweeper && System.currentTimeMillis() >= nextSweep) {
                    recover(consumer);
                    nextSweep = System.currentTimeMillis() + SWEEP_EVERY.toMillis();
                }
                for (MapRecord<String, Object, Object> record : queue.next(consumer, BLOCK)) {
                    process(record);
                }
            } catch (RuntimeException e) {
                if (running) {
                    log.error("Judge loop error, retrying shortly", e);
                    sleep(Duration.ofSeconds(2));
                }
            }
        }
    }

    private void recover(String consumer) {
        for (MapRecord<String, Object, Object> record : queue.claimAbandoned(consumer, ABANDONED_AFTER)) {
            log.warn("Reclaiming abandoned submission {}", record.getValue().get("id"));
            process(record);
        }
        for (UUID id : submissions.findStuck(ABANDONED_AFTER)) {
            log.warn("Re-queueing stuck submission {}", id);
            queue.enqueue(id);
        }
    }

    void process(MapRecord<String, Object, Object> record) {
        UUID id = SubmissionQueue.submissionId(record);
        Submission submission = submissions.findById(id).orElse(null);
        if (submission == null || !submissions.claimForJudging(id, ABANDONED_AFTER)) {
            queue.acknowledge(record.getId());
            return;
        }
        events.publish(id, SubmissionStatus.RUNNING, null);
        metrics.timer("codestep.queue.wait").record(Duration.between(submission.createdAt(), java.time.Instant.now()));

        Problem problem = problems.findBySlug(submission.problemSlug()).orElse(null);
        Timer.Sample sample = Timer.start(metrics);
        SubmissionResult result = problem == null
                ? SubmissionResult.internalError("This problem no longer exists.")
                : judge.judge(problem, submission.code(), submission.samplesOnly());
        sample.stop(metrics.timer("codestep.judge.duration", "verdict", result.verdict().name()));

        submissions.complete(id, result);
        events.publish(id, SubmissionStatus.DONE, result);
        queue.acknowledge(record.getId());
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "worker-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
