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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SubmissionFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static final CountDownLatch release = new CountDownLatch(1);

    @TestConfiguration
    static class FakeSandboxConfig {

        @Bean
        @Primary
        Sandbox fakeSandbox() {
            return (sources, mainClass, inputs) -> {
                if (sources.values().stream().anyMatch(code -> code.contains("WAIT"))) {
                    release.await(20, TimeUnit.SECONDS);
                }
                List<TestRun> runs = new ArrayList<>();
                boolean correct = sources.values().stream().anyMatch(code -> code.contains("CORRECT"));
                for (int i = 0; i < inputs.size(); i++) {
                    runs.add(new TestRun(0, correct ? expectedFor(inputs.get(i)) : "wrong", ""));
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

    @Test
    void submissionIsQueuedJudgedAndStored() {
        UUID id = submit("client-a", "CORRECT");

        Map<String, Object> view = awaitResult(id);

        assertThat(view.get("status")).isEqualTo("DONE");
        assertThat(((Map<?, ?>) view.get("result")).get("verdict")).isEqualTo("ACCEPTED");
        Submission stored = submissions.findById(id).orElseThrow();
        assertThat(stored.startedAt()).isNotNull();
        assertThat(stored.finishedAt()).isNotNull();
    }

    @Test
    void wrongAnswerIsReported() {
        Map<String, Object> view = awaitResult(submit("client-b", "nope"));

        assertThat(((Map<?, ?>) view.get("result")).get("verdict")).isEqualTo("WRONG_ANSWER");
    }

    @Test
    void historyListsSubmissionsNewestFirst() {
        UUID first = submit("client-c", "nope");
        awaitResult(first);
        UUID second = submit("client-c", "CORRECT");
        awaitResult(second);

        ResponseEntity<List<Map<String, Object>>> response = http.exchange(
                "/api/submissions?problem=find-max&clientId=client-c", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getBody()).extracting(row -> row.get("id")).containsExactly(second.toString(), first.toString());
    }

    @Test
    void eventStreamDeliversTheResult() throws Exception {
        UUID id = submit("client-d", "CORRECT");

        CompletableFuture<String> stream = CompletableFuture.supplyAsync(() ->
                http.getForObject("/api/submissions/" + id + "/events", String.class));

        assertThat(stream.get(20, TimeUnit.SECONDS)).contains("event:result").contains("ACCEPTED");
    }

    @Test
    void parallelSubmissionsFromOneClientAreLimited() throws Exception {
        List<CompletableFuture<HttpStatus>> requests = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            requests.add(CompletableFuture.supplyAsync(() ->
                    (HttpStatus) post("client-flood", "WAIT CORRECT").getStatusCode()));
        }
        List<HttpStatus> statuses = new ArrayList<>();
        for (CompletableFuture<HttpStatus> request : requests) {
            statuses.add(request.get(20, TimeUnit.SECONDS));
        }
        release.countDown();

        assertThat(statuses).filteredOn(s -> s == HttpStatus.ACCEPTED).hasSize(3);
        assertThat(statuses).filteredOn(s -> s == HttpStatus.TOO_MANY_REQUESTS).hasSize(3);
    }

    @Test
    void unknownProblemIsRejected() {
        ResponseEntity<String> response = http.postForEntity("/api/submissions",
                json(Map.of("slug", "nope", "code", "x", "samplesOnly", false, "clientId", "client-e")), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private UUID submit(String clientId, String code) {
        ResponseEntity<Map<String, Object>> response = post(clientId, code);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        return UUID.fromString(String.valueOf(response.getBody().get("id")));
    }

    private ResponseEntity<Map<String, Object>> post(String clientId, String code) {
        return http.exchange("/api/submissions", HttpMethod.POST,
                json(Map.of("slug", "find-max", "code", code, "samplesOnly", false, "clientId", clientId)),
                new ParameterizedTypeReference<>() {
                });
    }

    private Map<String, Object> awaitResult(UUID id) {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            ResponseEntity<Map<String, Object>> response = http.exchange("/api/submissions/" + id, HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {
                    });
            if (response.getBody() != null && response.getBody().get("result") != null) {
                return response.getBody();
            }
            sleep();
        }
        throw new AssertionError("Submission " + id + " was not judged in time");
    }

    private static HttpEntity<Map<String, Object>> json(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private static void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
