package com.codestep.judge;

import org.junit.jupiter.api.Assumptions;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

final class RunnerImage {

    static final String NAME = "codestep-runner:latest";

    private RunnerImage() {
    }

    static void require() {
        boolean available = isAvailable();
        if (System.getenv("CI") != null) {
            assertThat(available).as("runner image %s must be built before integration tests", NAME).isTrue();
        }
        Assumptions.assumeTrue(available, "Docker or the runner image is not available");
    }

    static DockerSandbox sandbox() {
        return new DockerSandbox(NAME, "", 2);
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
