import java.io.ByteArrayInputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

public final class TestRunner {

    private static final int MAX_STDOUT = 64 * 1024;
    private static final int MAX_STDERR = 4 * 1024;
    private static final int MAX_FRAMES = 12;
    private static final int KILLED = 137;
    private static final long THREAD_STACK_BYTES = 64L * 1024 * 1024;

    private static final PrintStream PROTOCOL =
            new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);

    private static volatile boolean exitAllowed;

    public static void main(String[] args) throws Exception {
        String mainClassName = args[0];
        long limitMillis = Long.parseLong(args[1]) * 1000;
        Path testsDir = Path.of(args[2]);
        URL classesUrl = Path.of(args[3]).toUri().toURL();

        List<Path> tests;
        try (Stream<Path> files = Files.list(testsDir)) {
            tests = files.filter(p -> p.toString().endsWith(".in")).sorted().toList();
        }

        installExitGuard();

        for (Path test : tests) {
            String name = test.getFileName().toString().replace(".in", "");
            LimitedBuffer out = new LimitedBuffer(MAX_STDOUT);
            LimitedBuffer err = new LimitedBuffer(MAX_STDERR);
            int[] exitCode = {0};

            System.setIn(new ByteArrayInputStream(Files.readAllBytes(test)));
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));

            Thread worker = new Thread(null, () -> exitCode[0] = runOnce(mainClassName, classesUrl, err),
                    "main", THREAD_STACK_BYTES);
            worker.setDaemon(true);
            worker.start();
            worker.join(limitMillis);

            System.out.flush();
            System.err.flush();
            if (worker.isAlive()) {
                report(name, KILLED, out, err);
                exit();
            }
            report(name, exitCode[0], out, err);
        }
        exit();
    }

    private static int runOnce(String mainClassName, URL classesUrl, LimitedBuffer err) {
        try (URLClassLoader loader = new URLClassLoader(new URL[]{classesUrl}, ClassLoader.getPlatformClassLoader())) {
            Class<?> mainClass = Class.forName(mainClassName, true, loader);
            Method main;
            try {
                main = mainClass.getMethod("main", String[].class);
            } catch (NoSuchMethodException e) {
                writeText(err, "Error: Main method not found in class " + mainClassName + "\n");
                return 1;
            }
            main.setAccessible(true);
            main.invoke(null, (Object) new String[0]);
            return 0;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ExitRequested exit) {
                return exit.status;
            }
            writeTrace(err, cause);
            return 1;
        } catch (ClassNotFoundException e) {
            writeText(err, "Error: Could not find or load main class " + mainClassName + "\n");
            return 1;
        } catch (Throwable t) {
            writeTrace(err, t);
            return 1;
        }
    }

    private static void writeTrace(LimitedBuffer err, Throwable t) {
        StringBuilder trace = new StringBuilder("Exception in thread \"main\" ").append(t).append('\n');
        int frames = 0;
        for (StackTraceElement frame : t.getStackTrace()) {
            String cls = frame.getClassName();
            if (cls.startsWith("jdk.internal.") || cls.startsWith("java.lang.reflect.") || cls.equals("TestRunner")) {
                continue;
            }
            if (frames++ == MAX_FRAMES) {
                trace.append("\t...\n");
                break;
            }
            trace.append("\tat ").append(frame).append('\n');
        }
        writeText(err, trace.toString());
    }

    private static void writeText(LimitedBuffer buffer, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        buffer.write(bytes, 0, bytes.length);
    }

    private static void report(String name, int exitCode, LimitedBuffer out, LimitedBuffer err) {
        Base64.Encoder base64 = Base64.getEncoder();
        PROTOCOL.println("TEST " + name + " " + exitCode);
        PROTOCOL.println(base64.encodeToString(out.toByteArray()));
        PROTOCOL.println(base64.encodeToString(err.toByteArray()));
    }

    private static void exit() {
        PROTOCOL.flush();
        exitAllowed = true;
        Runtime.getRuntime().halt(0);
    }

    /**
     * Every other permission check goes to the policy (runner.policy), which trusts this class and
     * grants the submission's classes nothing beyond reading system properties.
     */
    @SuppressWarnings("removal")
    private static void installExitGuard() {
        System.setSecurityManager(new SecurityManager() {
            @Override
            public void checkExit(int status) {
                if (!exitAllowed) {
                    throw new ExitRequested(status);
                }
                super.checkExit(status);
            }
        });
    }

    private static final class ExitRequested extends SecurityException {
        final int status;

        ExitRequested(int status) {
            super("System.exit(" + status + ")");
            this.status = status;
        }
    }

    private static final class LimitedBuffer extends OutputStream {
        private final byte[] data;
        private int size;

        LimitedBuffer(int capacity) {
            this.data = new byte[capacity];
        }

        @Override
        public synchronized void write(int b) {
            if (size < data.length) {
                data[size++] = (byte) b;
            }
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            int n = Math.min(len, data.length - size);
            if (n > 0) {
                System.arraycopy(b, off, data, size, n);
                size += n;
            }
        }

        synchronized byte[] toByteArray() {
            return java.util.Arrays.copyOf(data, size);
        }
    }
}
