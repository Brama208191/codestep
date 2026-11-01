package com.codestep.judge;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Judges through runner/judged.sh in the same container: the tar goes over a loopback socket, the
 * job runs as the unprivileged `judge` user, and the protocol comes back. Used where Docker isn't
 * available, such as Railway. The isolation is weaker than a container per submission; see the README.
 */
@Component
@ConditionalOnProperty(name = "judge.mode", havingValue = "local")
public class LocalSandbox implements Sandbox {

    /** run.sh allows 20 s to compile; this covers that plus process start-up. */
    private static final long STARTUP_ALLOWANCE_SECONDS = 30;
    private static final int CONNECT_TIMEOUT_MILLIS = 2_000;
    private static final int MAX_OUTPUT_BYTES = 8 * 1024 * 1024;

    private final String host;
    private final int port;
    private final int timeLimitSeconds;
    private final long startupAllowanceSeconds;

    @Autowired
    public LocalSandbox(@Value("${judge.local.host:127.0.0.1}") String host,
                        @Value("${judge.local.port:7070}") int port,
                        @Value("${judge.time-limit-seconds}") int timeLimitSeconds) {
        this(host, port, timeLimitSeconds, STARTUP_ALLOWANCE_SECONDS);
    }

    LocalSandbox(String host, int port, int timeLimitSeconds, long startupAllowanceSeconds) {
        this.host = host;
        this.port = port;
        this.timeLimitSeconds = timeLimitSeconds;
        this.startupAllowanceSeconds = startupAllowanceSeconds;
    }

    @Override
    public SandboxOutput execute(Map<String, String> sources, String mainClass, List<String> inputs)
            throws IOException, SandboxTimeoutException {
        byte[] job = DockerSandbox.framed(DockerSandbox.archive(sources, mainClass, timeLimitSeconds, inputs));
        long deadline = System.nanoTime() + ((long) inputs.size() * timeLimitSeconds + startupAllowanceSeconds) * 1_000_000_000L;

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
            socket.getOutputStream().write(job);
            socket.getOutputStream().flush();
            return SandboxOutput.parse(readUntil(socket, deadline));
        }
    }

    private static String readUntil(Socket socket, long deadline) throws IOException, SandboxTimeoutException {
        InputStream in = socket.getInputStream();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        while (true) {
            long remainingMillis = (deadline - System.nanoTime()) / 1_000_000;
            if (remainingMillis <= 0) {
                throw new SandboxTimeoutException();
            }
            socket.setSoTimeout((int) Math.min(remainingMillis, Integer.MAX_VALUE));
            int n;
            try {
                n = in.read(buffer);
            } catch (SocketTimeoutException e) {
                throw new SandboxTimeoutException();
            }
            if (n < 0) {
                return output.toString(StandardCharsets.UTF_8);
            }
            if (output.size() + n > MAX_OUTPUT_BYTES) {
                throw new IOException("Judge output exceeded " + MAX_OUTPUT_BYTES + " bytes");
            }
            output.write(buffer, 0, n);
        }
    }
}
