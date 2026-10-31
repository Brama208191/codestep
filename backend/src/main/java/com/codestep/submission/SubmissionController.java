package com.codestep.submission;

import com.codestep.judge.SubmissionResult;
import com.codestep.judge.Verdict;
import com.codestep.problem.ProblemRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/submissions")
@ConditionalOnProperty(name = "codestep.api.enabled", havingValue = "true", matchIfMissing = true)
public class SubmissionController {

    private static final int HISTORY_LIMIT = 20;

    private final ProblemRepository problems;
    private final SubmissionRepository submissions;
    private final SubmissionQueue queue;
    private final SseHub hub;
    private final Counter created;
    private final int maxInFlight;
    private final int maxQueued;

    public SubmissionController(ProblemRepository problems,
                                SubmissionRepository submissions,
                                SubmissionQueue queue,
                                SseHub hub,
                                MeterRegistry metrics,
                                @Value("${codestep.api.max-in-flight-per-client}") int maxInFlight,
                                @Value("${codestep.api.max-queued}") int maxQueued) {
        this.problems = problems;
        this.submissions = submissions;
        this.queue = queue;
        this.hub = hub;
        this.maxInFlight = maxInFlight;
        this.maxQueued = maxQueued;
        this.created = Counter.builder("codestep.submissions.created").register(metrics);
        Gauge.builder("codestep.queue.waiting", submissions, repo -> repo.countByStatus(SubmissionStatus.QUEUED))
                .register(metrics);
    }

    @PostMapping
    public ResponseEntity<Accepted> create(@Valid @RequestBody CreateSubmission request) {
        if (problems.findBySlug(request.slug()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Problem not found");
        }
        if (submissions.countByStatus(SubmissionStatus.QUEUED) >= maxQueued) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The judge is very busy right now. Please try again in a minute.");
        }
        Submission submission = submissions
                .createIfUnderLimit(request.clientId(), request.slug(), request.code(), request.samplesOnly(), maxInFlight)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "You already have submissions being judged. Wait for them to finish."));
        queue.enqueue(submission.id());
        created.increment();
        return ResponseEntity.accepted().body(new Accepted(submission.id(), submission.status()));
    }

    @GetMapping("/{id}")
    public SubmissionView get(@PathVariable UUID id) {
        Submission submission = find(id);
        return new SubmissionView(submission.id(), submission.status(), submission.result());
    }

    @GetMapping(path = "/{id}/events", produces = "text/event-stream")
    public SseEmitter events(@PathVariable UUID id) {
        find(id);
        SseEmitter emitter = hub.subscribe(id);
        Submission current = find(id);
        if (current.status() == SubmissionStatus.DONE) {
            hub.sendResult(emitter, current.result());
        } else {
            hub.sendStatus(emitter, current.status());
        }
        return emitter;
    }

    @GetMapping
    public List<SubmissionSummary> history(@RequestParam("problem") String slug, @RequestParam String clientId) {
        return submissions.findHistory(clientId, slug, HISTORY_LIMIT).stream().map(SubmissionSummary::from).toList();
    }

    private Submission find(UUID id) {
        return submissions.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found"));
    }

    public record CreateSubmission(
            @NotBlank String slug,
            @NotBlank @Size(max = 50_000) String code,
            boolean samplesOnly,
            @NotBlank @Size(max = 64) String clientId
    ) {
    }

    public record Accepted(UUID id, SubmissionStatus status) {
    }

    public record SubmissionView(UUID id, SubmissionStatus status, SubmissionResult result) {
    }

    public record SubmissionSummary(UUID id, SubmissionStatus status, Verdict verdict, boolean samplesOnly,
                                    int passed, int total, Instant createdAt, String code) {

        static SubmissionSummary from(Submission s) {
            SubmissionResult r = s.result();
            return new SubmissionSummary(s.id(), s.status(), r == null ? null : r.verdict(), s.samplesOnly(),
                    r == null ? 0 : r.passed(), r == null ? 0 : r.total(), s.createdAt(), s.code());
        }
    }
}
