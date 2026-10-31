package com.codestep.judge;

import com.codestep.problem.Problem;
import com.codestep.problem.ProblemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceSolutionsIT {

    private static JudgeService judge;

    @BeforeAll
    static void setUp() {
        RunnerImage.require();
        judge = new JudgeService(RunnerImage.sandbox(), new ErrorExplainer(), 2);
    }

    @TestFactory
    Stream<DynamicTest> everyProblemAcceptsItsReferenceSolution() throws IOException {
        return new ProblemRepository(new ObjectMapper()).findAll().stream()
                .map(problem -> DynamicTest.dynamicTest(problem.slug(), () -> {
                    SubmissionResult result = judge.judge(problem, solutionFor(problem), false);
                    assertThat(result.verdict()).as(String.valueOf(result)).isEqualTo(Verdict.ACCEPTED);
                    assertThat(result.passed()).isEqualTo(problem.tests().size());
                }));
    }

    private static String solutionFor(Problem problem) throws IOException {
        try (InputStream in = ReferenceSolutionsIT.class.getResourceAsStream("/solutions/" + problem.slug() + ".java")) {
            assertThat(in).as("missing reference solution for %s", problem.slug()).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
