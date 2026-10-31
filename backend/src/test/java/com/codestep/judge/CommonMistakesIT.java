package com.codestep.judge;

import com.codestep.problem.ProblemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CommonMistakesIT {

    private static JudgeService judge;
    private static ProblemRepository problems;

    @BeforeAll
    static void setUp() throws IOException {
        RunnerImage.require();
        judge = new JudgeService(RunnerImage.sandbox(), new ErrorExplainer(), 2);
        problems = new ProblemRepository(new ObjectMapper());
    }

    static Stream<Arguments> buggySolutions() {
        return Stream.of(
                Arguments.of("find-max", "Start `biggest` at `nums[0]`", """
                        class Solution {
                            public int findMax(int[] nums) {
                                int biggest = 0;
                                for (int i = 0; i < nums.length; i++) {
                                    if (nums[i] > biggest) biggest = nums[i];
                                }
                                return biggest;
                            }
                        }
                        """),
                Arguments.of("find-max", "You skipped the last number", """
                        class Solution {
                            public int findMax(int[] nums) {
                                int biggest = nums[0];
                                for (int i = 0; i < nums.length - 1; i++) {
                                    if (nums[i] > biggest) biggest = nums[i];
                                }
                                return biggest;
                            }
                        }
                        """),
                Arguments.of("sum-of-array", "`i <= nums.length`", """
                        class Solution {
                            public int sum(int[] nums) {
                                int total = 0;
                                for (int i = 0; i <= nums.length; i++) total += nums[i];
                                return total;
                            }
                        }
                        """),
                Arguments.of("sum-of-array", "This array is empty", """
                        class Solution {
                            public int sum(int[] nums) {
                                int total = nums[0];
                                for (int i = 1; i < nums.length; i++) total += nums[i];
                                return total;
                            }
                        }
                        """),
                Arguments.of("contains-number", "put `return false` after the loop", """
                        class Solution {
                            public boolean contains(int[] nums, int target) {
                                for (int i = 0; i < nums.length; i++) {
                                    if (nums[i] == target) return true;
                                    else return false;
                                }
                                return false;
                            }
                        }
                        """),
                Arguments.of("all-positive", "0 is not positive", """
                        class Solution {
                            public boolean allPositive(int[] nums) {
                                for (int x : nums) if (x < 0) return false;
                                return true;
                            }
                        }
                        """),
                Arguments.of("count-evens", "0 and negative numbers can be even", """
                        class Solution {
                            public int countEvens(int[] nums) {
                                int count = 0;
                                for (int x : nums) if (x > 0 && x % 2 == 0) count++;
                                return count;
                            }
                        }
                        """),
                Arguments.of("running-sum", "running `total` variable", """
                        class Solution {
                            public int[] runningSum(int[] nums) {
                                int[] result = new int[nums.length];
                                result[0] = nums[0];
                                for (int i = 1; i < nums.length; i++) result[i] = nums[i] + nums[i - 1];
                                return result;
                            }
                        }
                        """),
                Arguments.of("longest-word", "strictly longer", """
                        import java.util.Scanner;
                        public class Main {
                            public static void main(String[] args) {
                                String best = "";
                                for (String w : new Scanner(System.in).nextLine().split(" ")) {
                                    if (w.length() >= best.length()) best = w;
                                }
                                System.out.println(best);
                            }
                        }
                        """),
                Arguments.of("most-frequent", "return the smaller number", """
                        import java.util.*;
                        class Solution {
                            public int mostFrequent(int[] nums) {
                                Map<Integer, Integer> counts = new LinkedHashMap<>();
                                for (int x : nums) counts.merge(x, 1, Integer::sum);
                                int best = nums[0], bestCount = 0;
                                for (var e : counts.entrySet()) {
                                    if (e.getValue() > bestCount) { best = e.getKey(); bestCount = e.getValue(); }
                                }
                                return best;
                            }
                        }
                        """),
                Arguments.of("two-sum", "paired a number with itself", """
                        import java.util.Scanner;
                        public class Main {
                            public static void main(String[] args) {
                                Scanner in = new Scanner(System.in);
                                int n = in.nextInt();
                                int[] a = new int[n];
                                for (int i = 0; i < n; i++) a[i] = in.nextInt();
                                int t = in.nextInt();
                                for (int i = 0; i < n; i++)
                                    for (int j = 0; j < n; j++)
                                        if (a[i] + a[j] == t) { System.out.println(i + " " + j); return; }
                            }
                        }
                        """));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("buggySolutions")
    void typicalBeginnerBugGetsTargetedHint(String slug, String expectedHint, String buggyCode) {
        SubmissionResult result = judge.judge(problems.findBySlug(slug).orElseThrow(), buggyCode, false);

        assertThat(result.verdict()).isNotEqualTo(Verdict.ACCEPTED);
        assertThat(result.commonMistake()).as(String.valueOf(result)).isTrue();
        assertThat(result.explanation()).contains(expectedHint);
    }
}
