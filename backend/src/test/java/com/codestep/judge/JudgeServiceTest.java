package com.codestep.judge;

import com.codestep.judge.SandboxOutput.TestRun;
import com.codestep.problem.Mistake;
import com.codestep.problem.Mode;
import com.codestep.problem.Problem;
import com.codestep.problem.TestCase;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JudgeServiceTest {

    private static final Problem METHOD_PROBLEM = new Problem("sum", "Sum", "Arrays", "Running total", 1, 1,
            Mode.METHOD, "", "", "HARNESS", List.of(), null, List.of(), List.of(
            new TestCase("1\n1\n", "1", "nums = [1]", true),
            new TestCase("2\n1 2\n", "3", "nums = [1, 2]", false),
            new TestCase("0\n\n", "0", "nums = []", false)));

    private static final Problem MISTAKE_PROBLEM = new Problem("max", "Max", "Arrays", "Best so far", 3, 1,
            Mode.METHOD, "", "", "HARNESS", List.of(), null, List.of(), List.of(
            new TestCase("2\n-5 -2\n", "-2", "nums = [-5, -2]", true, List.of(
                    new Mistake("0", null, "Start biggest at nums[0]."),
                    new Mistake(null, "ArrayIndexOutOfBoundsException", "Use i < nums.length.")))));

    private static final Problem PROGRAM_PROBLEM = new Problem("prog", "Prog", "Arrays", "Counting", 2, 1,
            Mode.PROGRAM, "", "", null, List.of(), null, List.of(), List.of(
            new TestCase("x", "ok", null, true)));

    private final RecordingSandbox sandbox = new RecordingSandbox();
    private final JudgeService judge = new JudgeService(sandbox, new ErrorExplainer(), 2);

    @Test
    void acceptsWhenEveryTestMatches() {
        sandbox.respond(compiled(run(0, "1\n"), run(0, "3"), run(0, "0\n\n")));

        SubmissionResult result = judge.judge(METHOD_PROBLEM, "code", false);

        assertThat(result.verdict()).isEqualTo(Verdict.ACCEPTED);
        assertThat(result.passed()).isEqualTo(3);
        assertThat(result.total()).isEqualTo(3);
    }

    @Test
    void ignoresTrailingWhitespaceAndWindowsNewlines() {
        sandbox.respond(compiled(run(0, "1   \r\n"), run(0, "3\r\n"), run(0, "0")));

        assertThat(judge.judge(METHOD_PROBLEM, "code", false).verdict()).isEqualTo(Verdict.ACCEPTED);
    }

    @Test
    void reportsFirstWrongAnswerWithDisplayInput() {
        sandbox.respond(compiled(run(0, "1"), run(0, "4"), run(0, "0")));

        SubmissionResult result = judge.judge(METHOD_PROBLEM, "code", false);

        assertThat(result.verdict()).isEqualTo(Verdict.WRONG_ANSWER);
        assertThat(result.passed()).isEqualTo(1);
        assertThat(result.failedCase().number()).isEqualTo(2);
        assertThat(result.failedCase().sample()).isFalse();
        assertThat(result.failedCase().input()).isEqualTo("nums = [1, 2]");
        assertThat(result.failedCase().expected()).isEqualTo("3");
        assertThat(result.failedCase().actual()).isEqualTo("4");
    }

    @Test
    void knownWrongOutputGetsTargetedHint() {
        sandbox.respond(compiled(run(0, "0\n")));

        SubmissionResult result = judge.judge(MISTAKE_PROBLEM, "code", false);

        assertThat(result.verdict()).isEqualTo(Verdict.WRONG_ANSWER);
        assertThat(result.commonMistake()).isTrue();
        assertThat(result.explanation()).isEqualTo("Start biggest at nums[0].");
    }

    @Test
    void unknownWrongOutputFallsBackToGenericHint() {
        sandbox.respond(compiled(run(0, "7")));

        SubmissionResult result = judge.judge(MISTAKE_PROBLEM, "code", false);

        assertThat(result.commonMistake()).isFalse();
        assertThat(result.explanation()).contains("Trace the input by hand");
    }

    @Test
    void knownExceptionGetsTargetedHint() {
        sandbox.respond(compiled(run(1, "", "Exception in thread \"main\" java.lang.ArrayIndexOutOfBoundsException: Index 2")));

        SubmissionResult result = judge.judge(MISTAKE_PROBLEM, "code", false);

        assertThat(result.verdict()).isEqualTo(Verdict.RUNTIME_ERROR);
        assertThat(result.commonMistake()).isTrue();
        assertThat(result.explanation()).isEqualTo("Use i < nums.length.");
    }

    @Test
    void outputMistakesDoNotMatchExceptions() {
        sandbox.respond(compiled(run(1, "", "Exception in thread \"main\" java.lang.NullPointerException")));

        SubmissionResult result = judge.judge(MISTAKE_PROBLEM, "code", false);

        assertThat(result.commonMistake()).isFalse();
        assertThat(result.explanation()).contains("NullPointerException");
    }

    @Test
    void classifiesRuntimeErrors() {
        sandbox.respond(compiled(run(1, "", "Exception in thread \"main\" java.lang.ArithmeticException: / by zero")));

        SubmissionResult result = judge.judge(METHOD_PROBLEM, "code", true);

        assertThat(result.verdict()).isEqualTo(Verdict.RUNTIME_ERROR);
        assertThat(result.explanation()).contains("dividing an integer by zero");
    }

    @Test
    void classifiesKilledProcessesAsTimeLimit() {
        sandbox.respond(compiled(run(137, "")));

        assertThat(judge.judge(METHOD_PROBLEM, "code", true).verdict()).isEqualTo(Verdict.TIME_LIMIT_EXCEEDED);
    }

    @Test
    void classifiesMissingRunsAsTimeLimit() {
        sandbox.respond(compiled(run(0, "1")));

        SubmissionResult result = judge.judge(METHOD_PROBLEM, "code", false);

        assertThat(result.verdict()).isEqualTo(Verdict.TIME_LIMIT_EXCEEDED);
        assertThat(result.passed()).isEqualTo(1);
    }

    @Test
    void classifiesOutOfMemory() {
        sandbox.respond(compiled(run(1, "", "Exception in thread \"main\" java.lang.OutOfMemoryError: Java heap space")));

        assertThat(judge.judge(METHOD_PROBLEM, "code", true).verdict()).isEqualTo(Verdict.MEMORY_LIMIT_EXCEEDED);
    }

    @Test
    void reportsCompileErrorsWithCleanPaths() {
        sandbox.respond(new SandboxOutput(false, "/tmp/work/src/Solution.java:1: error: ';' expected", List.of()));

        SubmissionResult result = judge.judge(METHOD_PROBLEM, "code", false);

        assertThat(result.verdict()).isEqualTo(Verdict.COMPILE_ERROR);
        assertThat(result.compileOutput()).startsWith("Solution.java:1");
        assertThat(result.explanation()).contains("semicolon");
    }

    @Test
    void wholeSandboxTimeoutIsTimeLimit() {
        sandbox.timeout = true;

        assertThat(judge.judge(METHOD_PROBLEM, "code", false).verdict()).isEqualTo(Verdict.TIME_LIMIT_EXCEEDED);
    }

    @Test
    void sandboxFailureIsInternalError() {
        sandbox.failure = new IOException("docker is not running");

        assertThat(judge.judge(METHOD_PROBLEM, "code", false).verdict()).isEqualTo(Verdict.INTERNAL_ERROR);
    }

    @Test
    void methodProblemsRunUserCodeWithHarness() {
        sandbox.respond(compiled(run(0, "1")));

        judge.judge(METHOD_PROBLEM, "USER", true);

        assertThat(sandbox.lastSources).containsEntry("Solution.java", "USER").containsEntry("Main.java", "HARNESS");
        assertThat(sandbox.lastMainClass).isEqualTo("Main");
        assertThat(sandbox.lastInputs).containsExactly("1\n1\n");
    }

    @Test
    void programProblemsRunDetectedMainClass() {
        sandbox.respond(compiled(run(0, "ok")));

        judge.judge(PROGRAM_PROBLEM, "public class Solver { public static void main(String[] a) {} }", false);

        assertThat(sandbox.lastSources).containsOnlyKeys("Solver.java");
        assertThat(sandbox.lastMainClass).isEqualTo("Solver");
    }

    private static SandboxOutput compiled(TestRun... runs) {
        return new SandboxOutput(true, null, List.of(runs));
    }

    private static TestRun run(int exitCode, String stdout) {
        return new TestRun(exitCode, stdout, "");
    }

    private static TestRun run(int exitCode, String stdout, String stderr) {
        return new TestRun(exitCode, stdout, stderr);
    }

    private static final class RecordingSandbox implements Sandbox {
        private SandboxOutput response;
        private boolean timeout;
        private IOException failure;
        private Map<String, String> lastSources;
        private String lastMainClass;
        private List<String> lastInputs = new ArrayList<>();

        void respond(SandboxOutput output) {
            this.response = output;
        }

        @Override
        public SandboxOutput execute(Map<String, String> sources, String mainClass, List<String> inputs)
                throws IOException, SandboxTimeoutException {
            lastSources = sources;
            lastMainClass = mainClass;
            lastInputs = inputs;
            if (failure != null) {
                throw failure;
            }
            if (timeout) {
                throw new SandboxTimeoutException();
            }
            return response;
        }
    }
}
