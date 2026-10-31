package com.codestep.judge;

import com.codestep.judge.SandboxOutput.TestRun;
import com.codestep.judge.SubmissionResult.FailedCase;
import com.codestep.problem.Mistake;
import com.codestep.problem.Mode;
import com.codestep.problem.Problem;
import com.codestep.problem.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class JudgeService {

    private static final Logger log = LoggerFactory.getLogger(JudgeService.class);
    private static final int KILLED_EXIT_CODE = 137;

    private final Sandbox sandbox;
    private final ErrorExplainer explainer;
    private final int timeLimitSeconds;

    public JudgeService(Sandbox sandbox,
                        ErrorExplainer explainer,
                        @Value("${judge.time-limit-seconds}") int timeLimitSeconds) {
        this.sandbox = sandbox;
        this.explainer = explainer;
        this.timeLimitSeconds = timeLimitSeconds;
    }

    public SubmissionResult judge(Problem problem, String code, boolean samplesOnly) {
        List<TestCase> tests = samplesOnly ? problem.sampleTests() : problem.tests();
        long start = System.nanoTime();
        SandboxOutput output;
        try {
            output = runInSandbox(problem, code, tests);
        } catch (SandboxTimeoutException e) {
            return SubmissionResult.failed(Verdict.TIME_LIMIT_EXCEEDED, 0, tests.size(), null,
                    explainer.explainTimeLimit(timeLimitSeconds), elapsedMs(start));
        } catch (IOException | IllegalStateException e) {
            log.error("Sandbox failure", e);
            return SubmissionResult.internalError("The judge had a problem running your code. Please try again.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SubmissionResult.internalError("The judge was interrupted. Please try again.");
        }

        long runtimeMs = elapsedMs(start);
        if (!output.compiled()) {
            String compileOutput = explainer.cleanCompileOutput(output.compileOutput());
            return SubmissionResult.compileError(compileOutput, explainer.explainCompile(compileOutput), runtimeMs);
        }
        return grade(tests, output.runs(), runtimeMs);
    }

    private SandboxOutput runInSandbox(Problem problem, String code, List<TestCase> tests)
            throws IOException, InterruptedException, SandboxTimeoutException {
        if (problem.mode() == Mode.METHOD) {
            return sandbox.execute(Map.of("Solution.java", code, "Main.java", problem.harness()), "Main", inputs(tests));
        }
        ProgramEntry entry = ProgramEntry.detect(code);
        return sandbox.execute(Map.of(entry.fileName(), code), entry.mainClass(), inputs(tests));
    }

    private static List<String> inputs(List<TestCase> tests) {
        return tests.stream().map(TestCase::input).toList();
    }

    SubmissionResult grade(List<TestCase> tests, List<TestRun> runs, long runtimeMs) {
        for (int i = 0; i < tests.size(); i++) {
            TestCase test = tests.get(i);
            if (i >= runs.size()) {
                return SubmissionResult.failed(Verdict.TIME_LIMIT_EXCEEDED, i, tests.size(), failedCase(i, test, "", ""),
                        explainer.explainTimeLimit(timeLimitSeconds), runtimeMs);
            }
            TestRun run = runs.get(i);
            FailedCase failed = failedCase(i, test, run.stdout(), run.stderr());

            if (run.stderr().contains("OutOfMemoryError")) {
                return SubmissionResult.failed(Verdict.MEMORY_LIMIT_EXCEEDED, i, tests.size(), failed,
                        explainer.explainMemoryLimit(), runtimeMs);
            }
            if (run.exitCode() == KILLED_EXIT_CODE) {
                return SubmissionResult.failed(Verdict.TIME_LIMIT_EXCEEDED, i, tests.size(), failed,
                        explainer.explainTimeLimit(timeLimitSeconds), runtimeMs);
            }
            if (run.exitCode() != 0) {
                Optional<Mistake> mistake = test.mistakeForError(run.stderr());
                return SubmissionResult.failed(Verdict.RUNTIME_ERROR, i, tests.size(), failed,
                        mistake.map(Mistake::hint).orElseGet(() -> explainer.explainRuntime(run.stderr())),
                        mistake.isPresent(), runtimeMs);
            }
            String actual = normalize(run.stdout());
            if (!actual.equals(normalize(test.expected()))) {
                Optional<Mistake> mistake = test.mistakeForOutput(actual);
                return SubmissionResult.failed(Verdict.WRONG_ANSWER, i, tests.size(), failed,
                        mistake.map(Mistake::hint).orElseGet(explainer::explainWrongAnswer),
                        mistake.isPresent(), runtimeMs);
            }
        }
        return SubmissionResult.accepted(tests.size(), runtimeMs);
    }

    private FailedCase failedCase(int index, TestCase test, String actual, String stderr) {
        return new FailedCase(index + 1, test.sample(), test.displayInput(), test.expected(), actual, stderr);
    }

    static String normalize(String text) {
        return text.replace("\r\n", "\n")
                .lines()
                .map(String::stripTrailing)
                .collect(Collectors.joining("\n"))
                .strip();
    }

    private static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
