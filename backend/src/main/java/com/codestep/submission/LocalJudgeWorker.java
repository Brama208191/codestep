package com.codestep.submission;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Single-container mode: submissions go to an in-process queue instead of a Redis Stream. While
 * idle it sends no network traffic, so the platform can put the app to sleep. Postgres stays the
 * source of truth, so anything queued or running when the process stopped is re-queued at start-up.
 */
@Component
@ConditionalOnProperty(name = "codestep.queue", havingValue = "memory")
public class LocalJudgeWorker implements SubmissionDispatcher, SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(LocalJudgeWorker.class);
    private static final Duration CLAIM_STALE_AFTER = Duration.ofMinutes(2);

    private record Job(UUID id, Duration staleAfter) {
    }

    private final BlockingQueue<Job> queue = new LinkedBlockingQueue<>();
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private final SubmissionRepository submissions;
    private final SubmissionJudge judge;
    private final int threadCount;
    private final List<Thread> threads = new ArrayList<>();
    private volatile boolean running;

    public LocalJudgeWorker(SubmissionRepository submissions,
                            SubmissionJudge judge,
                            @Value("${codestep.worker.threads}") int threadCount) {
        this.submissions = submissions;
        this.judge = judge;
        this.threadCount = threadCount;
    }

    @Override
    public void enqueue(UUID submissionId) {
        offer(new Job(submissionId, CLAIM_STALE_AFTER));
    }

    private void offer(Job job) {
        if (pending.add(job.id())) {
            queue.add(job);
        }
    }

    /** Runs before the web server starts, so recovery can't race a new submission for the same id. */
    @Override
    public int getPhase() {
        return SmartLifecycle.DEFAULT_PHASE - 4096;
    }

    @Override
    public void start() {
        // Whatever is still queued or running belonged to a process that is gone, so claim it now.
        List<UUID> orphans = submissions.findStuck(Duration.ZERO);
        orphans.forEach(id -> offer(new Job(id, Duration.ZERO)));
        if (!orphans.isEmpty()) {
            log.warn("Re-queued {} submission(s) left over from the previous run", orphans.size());
        }
        running = true;
        for (int i = 0; i < threadCount; i++) {
            threads.add(Thread.ofPlatform().name("judge-" + i).daemon(true).start(this::loop));
        }
        log.info("In-process judge worker started with {} thread(s)", threadCount);
    }

    private void loop() {
        while (running) {
            Job job;
            try {
                job = queue.take();
            } catch (InterruptedException e) {
                return;
            }
            try {
                judge.judge(job.id(), job.staleAfter());
            } catch (RuntimeException e) {
                log.error("Judging submission {} failed", job.id(), e);
            } finally {
                pending.remove(job.id());
            }
        }
    }

    @Override
    public void stop() {
        running = false;
        for (Thread thread : threads) {
            thread.interrupt();
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
}
