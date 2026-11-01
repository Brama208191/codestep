package com.codestep.judge;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The client side of the judge service, against a fake judged.sh that reads jobs the way run.sh does. */
class LocalSandboxTest {

    private final ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());

    LocalSandboxTest() throws IOException {
    }

    @AfterEach
    void close() throws IOException {
        server.close();
    }

    private interface Judge {
        void serve(byte[] job, OutputStream out) throws Exception;
    }

    /** One framed job: the length line, then exactly that many bytes, with no half-close needed. */
    private CompletableFuture<Void> fakeJudge(Judge judge) {
        return CompletableFuture.runAsync(() -> {
            try (Socket socket = server.accept()) {
                InputStream in = new BufferedInputStream(socket.getInputStream());
                StringBuilder length = new StringBuilder();
                for (int c = in.read(); c != '\n'; c = in.read()) {
                    length.append((char) c);
                }
                byte[] job = in.readNBytes(Integer.parseInt(length.toString()));
                judge.serve(job, socket.getOutputStream());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Test
    void sendsAFramedArchiveAndParsesTheReply() throws Exception {
        AtomicReference<byte[]> received = new AtomicReference<>();
        CompletableFuture<Void> judge = fakeJudge((job, out) -> {
            received.set(job);
            String stdout = Base64.getEncoder().encodeToString("42\n".getBytes(StandardCharsets.UTF_8));
            out.write(("COMPILED\nTEST 000 0\n" + stdout + "\n\n").getBytes(StandardCharsets.UTF_8));
        });

        SandboxOutput output = new LocalSandbox("127.0.0.1", server.getLocalPort(), 1)
                .execute(Map.of("Main.java", "class Main {}"), "Main", List.of("1\n"));
        judge.join();

        assertThat(output.compiled()).isTrue();
        assertThat(output.runs()).singleElement().satisfies(run -> assertThat(run.stdout()).isEqualTo("42\n"));
        assertThat(received.get())
                .isEqualTo(DockerSandbox.archive(Map.of("Main.java", "class Main {}"), "Main", 1, List.of("1\n")));
    }

    @Test
    void aJudgeThatNeverAnswersEndsAsATimeLimitAtTheDeadline() {
        fakeJudge((job, out) -> Thread.sleep(30_000));
        LocalSandbox sandbox = new LocalSandbox("127.0.0.1", server.getLocalPort(), 1, 1);

        long start = System.nanoTime();
        assertThatThrownBy(() -> sandbox.execute(Map.of("Main.java", ""), "Main", List.of("1\n")))
                .isInstanceOf(SandboxTimeoutException.class);
        assertThat((System.nanoTime() - start) / 1_000_000).as("one test at 1 s plus 1 s allowance").isBetween(1_500L, 5_000L);
    }

    @Test
    void anUnreachableJudgeIsAnIoErrorNotAVerdict() throws IOException {
        int port = server.getLocalPort();
        server.close();

        assertThatThrownBy(() -> new LocalSandbox("127.0.0.1", port, 1).execute(Map.of(), "Main", List.of("1\n")))
                .isInstanceOf(IOException.class);
    }
}
