package com.codestep.judge;

import org.junit.jupiter.api.Assumptions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

final class RunnerImage {

    static final String NAME = "codestep-runner:latest";
    private static final int JUDGE_PORT = 7070;

    private static GenericContainer<?> judgeService;

    private RunnerImage() {
    }

    static void require() {
        boolean available = isAvailable();
        if (System.getenv("CI") != null) {
            assertThat(available).as("runner image %s must be built before integration tests", NAME).isTrue();
        }
        Assumptions.assumeTrue(available, "Docker or the runner image is not available");
    }

    /**
     * A container per submission by default. With {@code -Djudge.sandbox=local}, the same suites run
     * against the single-container judge instead: judged.sh in one long-lived runner container, exactly
     * as it runs on Railway, except that it listens on all interfaces so the test JVM can reach it.
     */
    static Sandbox sandbox() {
        if ("local".equals(System.getProperty("judge.sandbox"))) {
            GenericContainer<?> judge = judgeService();
            return new LocalSandbox(judge.getHost(), judge.getMappedPort(JUDGE_PORT), 2);
        }
        return new DockerSandbox(NAME, "", 2);
    }

    private static synchronized GenericContainer<?> judgeService() {
        if (judgeService == null) {
            judgeService = new GenericContainer<>(NAME)
                    .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("/usr/local/bin/judged.sh"))
                    .withEnv("JUDGE_BIND", "0.0.0.0")
                    .withExposedPorts(JUDGE_PORT)
                    .waitingFor(Wait.forListeningPort());
            judgeService.start();
        }
        return judgeService;
    }

    private static boolean isAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "image", "inspect", NAME).redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
