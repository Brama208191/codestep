package com.codestep.submission;

import com.codestep.judge.Sandbox;
import com.codestep.judge.SandboxOutput;
import com.codestep.judge.SandboxOutput.TestRun;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The "single" profile used on Railway: no Redis anywhere, an in-process queue, and the API also
 * enforcing what nginx did. The judge is faked here; the judge suites cover the real one.
 */
@Testcontainers
@ActiveProfiles("single")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "codestep.api.submissions-per-minute-per-ip=8")
class SingleContainerFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration
    static class FakeSandboxConfig {

        @Bean
        @Primary
        Sandbox fakeSandbox() {
            return (sources, mainClass, inputs) -> {
                boolean correct = sources.values().stream().anyMatch(code -> code.contains("CORRECT"));
                List<TestRun> runs = new ArrayList<>();
                for (String input : inputs) {
                    runs.add(new TestRun(0, correct ? expectedFor(input) : "wrong", ""));
                }
                return new SandboxOutput(true, null, runs);
            };
        }

        private static String expectedFor(String input) {
            String[] parts = input.trim().split("\\s+");
            int best = Integer.MIN_VALUE;
            for (int i = 1; i < parts.length; i++) {
                best = Math.max(best, Integer.parseInt(parts[i]));
            }
            return String.valueOf(best);
        }
    }

    @Autowired
    TestRestTemplate http;

    @Autowired
    SubmissionRepository submissions;

    @Autowired
    LocalJudgeWorker worker;

    @Test
    void aSubmissionIsJudgedThroughTheInProcessQueueAndStreamed() throws Exception {
        UUID id = submit("single-a", "CORRECT");

        CompletableFuture<String> stream = CompletableFuture.supplyAsync(() ->
                http.getForObject("/api/submissions/" + id + "/events", String.class));

        assertThat(stream.get(20, TimeUnit.SECONDS)).contains("event:result").contains("ACCEPTED");
        assertThat(submissions.findById(id).orElseThrow().status()).isEqualTo(SubmissionStatus.DONE);
    }

    @Test
    void submissionsLeftOverFromAStoppedProcessAreJudgedAtStartUp() {
        worker.stop();
        Submission queued = submissions.create("single-b", "find-max", "CORRECT", false);
        Submission running = submissions.create("single-b", "find-max", "CORRECT", false);
        submissions.claimForJudging(running.id(), Duration.ZERO);

        worker.start();

        assertThat(awaitDone(queued.id())).isEqualTo(SubmissionStatus.DONE);
        assertThat(awaitDone(running.id())).isEqualTo(SubmissionStatus.DONE);
    }

    @Test
    void submittingTooFastFromOneAddressIsRefused() {
        List<HttpStatus> statuses = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            statuses.add((HttpStatus) post("single-c-" + i, "CORRECT", "203.0.113.9").getStatusCode());
        }

        assertThat(statuses).contains(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(statuses).filteredOn(s -> s == HttpStatus.ACCEPTED).hasSizeLessThanOrEqualTo(8);
    }

    @Test
    void responsesCarryTheSecurityHeaders() {
        ResponseEntity<String> response = http.getForEntity("/api/problems", String.class);

        assertThat(response.getHeaders().getFirst("Content-Security-Policy")).contains("default-src 'self'");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().getFirst("X-Frame-Options")).isEqualTo("DENY");
    }

    @Test
    void onlyTheHealthEndpointIsPublic() {
        assertThat(http.getForEntity("/actuator/health", String.class).getBody()).contains("UP");
        assertThat(http.getForEntity("/actuator/prometheus", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private UUID submit(String clientId, String code) {
        ResponseEntity<Map<String, Object>> response = post(clientId, code);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        return UUID.fromString(String.valueOf(response.getBody().get("id")));
    }

    private ResponseEntity<Map<String, Object>> post(String clientId, String code) {
        return post(clientId, code, null);
    }

    /** {@code forwardedFor} gives the request its own rate-limit bucket, set the way Railway's edge does. */
    private ResponseEntity<Map<String, Object>> post(String clientId, String code, String forwardedFor) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (forwardedFor != null) {
            headers.set("X-Real-IP", forwardedFor);
        }
        return http.exchange("/api/submissions", HttpMethod.POST,
                new HttpEntity<>(Map.of("slug", "find-max", "code", code, "samplesOnly", false, "clientId", clientId), headers),
                new ParameterizedTypeReference<>() {
                });
    }

    private SubmissionStatus awaitDone(UUID id) {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            SubmissionStatus status = submissions.findById(id).orElseThrow().status();
            if (status == SubmissionStatus.DONE) {
                return status;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return submissions.findById(id).orElseThrow().status();
    }

    @Test
    void aClientWritingItsOwnForwardedForCannotDodgeTheLimit() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        List<HttpStatus> statuses = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            headers.set("X-Real-IP", "203.0.113.77");
            headers.set("X-Forwarded-For", "198.51.100." + (i + 1));
            statuses.add((HttpStatus) http.exchange("/api/submissions", HttpMethod.POST,
                    new HttpEntity<>(Map.of("slug", "find-max", "code", "CORRECT", "samplesOnly", false,
                            "clientId", "single-d-" + i), headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    }).getStatusCode());
        }

        assertThat(statuses).contains(HttpStatus.TOO_MANY_REQUESTS);
    }
}
