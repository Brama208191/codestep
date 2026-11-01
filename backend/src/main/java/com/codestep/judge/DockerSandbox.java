package com.codestep.judge;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "judge.mode", havingValue = "docker", matchIfMissing = true)
public class DockerSandbox implements Sandbox {

    private static final long STARTUP_ALLOWANCE_SECONDS = 15;

    private final String image;
    private final String runtime;
    private final int timeLimitSeconds;

    public DockerSandbox(@Value("${judge.image}") String image,
                         @Value("${judge.runtime:}") String runtime,
                         @Value("${judge.time-limit-seconds}") int timeLimitSeconds) {
        this.image = image;
        this.runtime = runtime;
        this.timeLimitSeconds = timeLimitSeconds;
    }

    @Override
    public SandboxOutput execute(Map<String, String> sources, String mainClass, List<String> inputs)
            throws IOException, InterruptedException, SandboxTimeoutException {
        String container = "codestep-judge-" + UUID.randomUUID();
        Process process = new ProcessBuilder(command(container, mainClass)).redirectErrorStream(true).start();
        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
            try {
                return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(framed(archive(sources, mainClass, timeLimitSeconds, inputs)));
        } catch (IOException ignored) {
        }

        long wallClock = (long) inputs.size() * timeLimitSeconds + STARTUP_ALLOWANCE_SECONDS;
        if (!process.waitFor(wallClock, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            new ProcessBuilder("docker", "rm", "-f", container).start().waitFor(30, TimeUnit.SECONDS);
            throw new SandboxTimeoutException();
        }
        return SandboxOutput.parse(output.join());
    }

    private List<String> command(String container, String mainClass) {
        List<String> command = new ArrayList<>(List.of("docker", "run", "-i", "--rm",
                "--name", container,
                "--network", "none",
                "--memory", "256m",
                "--memory-swap", "256m",
                "--cpus", "1",
                "--pids-limit", "128",
                "--cap-drop", "ALL",
                "--security-opt", "no-new-privileges",
                "--read-only",
                "--tmpfs", "/tmp:rw,size=64m",
                "--ulimit", "nofile=256:256",
                "--ulimit", "fsize=16777216:16777216",
                "--user", "65534:65534",
                "-e", "TIME_LIMIT=" + timeLimitSeconds,
                "-e", "MAIN_CLASS=" + mainClass));
        if (!runtime.isBlank()) {
            command.add("--runtime");
            command.add(runtime);
        }
        command.add(image);
        return command;
    }

    /** run.sh reads the archive's size on the first line, then exactly that many bytes. */
    static byte[] framed(byte[] archive) {
        byte[] header = (archive.length + "\n").getBytes(StandardCharsets.US_ASCII);
        byte[] job = Arrays.copyOf(header, header.length + archive.length);
        System.arraycopy(archive, 0, job, header.length, archive.length);
        return job;
    }

    /** The job for run.sh, which also reads the main class and time limit from {@code main} and {@code limit}. */
    static byte[] archive(Map<String, String> sources, String mainClass, int timeLimitSeconds, List<String> inputs)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(bytes)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            addFile(tar, "main", mainClass);
            addFile(tar, "limit", String.valueOf(timeLimitSeconds));
            addDirectory(tar, "src/");
            addDirectory(tar, "tests/");
            for (Map.Entry<String, String> source : sources.entrySet()) {
                addFile(tar, "src/" + source.getKey(), source.getValue());
            }
            for (int i = 0; i < inputs.size(); i++) {
                addFile(tar, "tests/%03d.in".formatted(i), inputs.get(i));
            }
        }
        return bytes.toByteArray();
    }

    private static void addDirectory(TarArchiveOutputStream tar, String name) throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setMode(040755);
        tar.putArchiveEntry(entry);
        tar.closeArchiveEntry();
    }

    private static void addFile(TarArchiveOutputStream tar, String name, String content) throws IOException {
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(data.length);
        entry.setMode(0100644);
        tar.putArchiveEntry(entry);
        tar.write(data);
        tar.closeArchiveEntry();
    }
}
