package com.codestep.judge;

import com.codestep.problem.Problem;
import com.codestep.problem.ProblemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class SandboxSecurityIT {

    private static JudgeService judge;
    private static ProblemRepository problems;

    @BeforeAll
    static void setUp() throws IOException {
        RunnerImage.require();
        judge = new JudgeService(RunnerImage.sandbox(), new ErrorExplainer(), 2);
        problems = new ProblemRepository(new ObjectMapper());
    }

    private static SubmissionResult run(String slug, String code) {
        Problem problem = problems.findBySlug(slug).orElseThrow();
        return judge.judge(problem, code, true);
    }

    @Test
    void infiniteLoopHitsTimeLimit() {
        SubmissionResult result = run("find-max", "class Solution { public int findMax(int[] n) { while (true) {} } }");

        assertThat(result.verdict()).isEqualTo(Verdict.TIME_LIMIT_EXCEEDED);
    }

    @Test
    void staticStateDoesNotLeakBetweenTests() {
        SubmissionResult result = run("count-evens", """
                class Solution {
                    static int count = 0;
                    public int countEvens(int[] nums) {
                        for (int x : nums) if (x % 2 == 0) count++;
                        return count;
                    }
                }
                """);

        assertThat(result.verdict()).isEqualTo(Verdict.ACCEPTED);
    }

    @Test
    void systemExitDoesNotStopRemainingTests() {
        SubmissionResult result = run("longest-word", """
                import java.util.Scanner;
                public class Main {
                    public static void main(String[] args) {
                        String best = "";
                        for (String w : new Scanner(System.in).nextLine().split(" ")) if (w.length() > best.length()) best = w;
                        System.out.println(best);
                        System.exit(0);
                    }
                }
                """);

        assertThat(result.verdict()).isEqualTo(Verdict.ACCEPTED);
        assertThat(result.passed()).isEqualTo(result.total());
    }

    @Test
    void networkIsUnavailable() {
        SubmissionResult result = run("find-max", """
                class Solution {
                    public int findMax(int[] n) {
                        try { new java.net.Socket("1.1.1.1", 80).close(); return n[0]; }
                        catch (Exception e) { throw new IllegalStateException("blocked"); }
                    }
                }
                """);

        assertThat(result.verdict()).isEqualTo(Verdict.RUNTIME_ERROR);
        assertThat(result.failedCase().stderr()).contains("blocked");
    }

    @Test
    void filesystemIsReadOnly() {
        SubmissionResult result = run("find-max", """
                class Solution {
                    public int findMax(int[] n) {
                        try { java.nio.file.Files.writeString(java.nio.file.Path.of("/opt/evil"), "x"); return n[0]; }
                        catch (Exception e) { throw new IllegalStateException("blocked"); }
                    }
                }
                """);

        assertThat(result.verdict()).isEqualTo(Verdict.RUNTIME_ERROR);
    }

    @Test
    void memoryBombIsContained() {
        SubmissionResult result = run("find-max", """
                class Solution {
                    public int findMax(int[] n) {
                        java.util.List<long[]> hog = new java.util.ArrayList<>();
                        while (true) hog.add(new long[1_000_000]);
                    }
                }
                """);

        assertThat(result.verdict()).isEqualTo(Verdict.MEMORY_LIMIT_EXCEEDED);
    }

    @Test
    void endlessOutputIsCapped() {
        SubmissionResult result = run("find-max",
                "class Solution { public int findMax(int[] n) { while (true) System.out.print(\"spam\"); } }");

        assertThat(result.verdict()).isEqualTo(Verdict.TIME_LIMIT_EXCEEDED);
        assertThat(result.failedCase().actual().length()).isLessThanOrEqualTo(64 * 1024);
    }

    @Test
    void nonPublicMainClassRuns() {
        SubmissionResult result = run("two-sum", """
                import java.util.*;
                class App {
                    public static void main(String[] args) {
                        Scanner in = new Scanner(System.in);
                        int n = in.nextInt();
                        int[] a = new int[n];
                        for (int i = 0; i < n; i++) a[i] = in.nextInt();
                        int t = in.nextInt();
                        Map<Integer, Integer> seen = new HashMap<>();
                        for (int j = 0; j < n; j++) {
                            Integer i = seen.get(t - a[j]);
                            if (i != null) { System.out.println(i + " " + j); return; }
                            seen.put(a[j], j);
                        }
                    }
                }
                """);

        assertThat(result.verdict()).isEqualTo(Verdict.ACCEPTED);
    }
}
