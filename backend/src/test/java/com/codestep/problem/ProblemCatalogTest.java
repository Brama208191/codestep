package com.codestep.problem;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemCatalogTest {

    private static List<Problem> problems;

    @BeforeAll
    static void load() throws IOException {
        problems = new ProblemRepository(new ObjectMapper()).findAll();
    }

    @Test
    void loadsProblemsInPathOrder() {
        assertThat(problems).isNotEmpty();
        assertThat(problems).extracting(Problem::order).isSorted();
    }

    @Test
    void slugsAndOrdersAreUnique() {
        Set<String> slugs = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (Problem p : problems) {
            assertThat(slugs.add(p.slug())).as("duplicate slug %s", p.slug()).isTrue();
            assertThat(orders.add(p.order())).as("duplicate order %s", p.order()).isTrue();
        }
    }

    @Test
    void everyProblemIsComplete() {
        for (Problem p : problems) {
            assertThat(p.title()).as(p.slug()).isNotBlank();
            assertThat(p.topic()).as(p.slug()).isNotBlank();
            assertThat(p.pattern()).as(p.slug()).isNotBlank();
            assertThat(p.description()).as(p.slug()).isNotBlank();
            assertThat(p.starterCode()).as(p.slug()).isNotBlank();
            assertThat(p.hints()).as(p.slug()).hasSizeGreaterThanOrEqualTo(3);
            assertThat(p.workedExample()).as(p.slug()).isNotNull();
            assertThat(p.walkthrough()).as(p.slug()).isNotEmpty();
            assertThat(p.sampleTests()).as(p.slug() + " samples").isNotEmpty();
            assertThat(p.tests().size()).as(p.slug() + " hidden tests").isGreaterThan(p.sampleTests().size());
        }
    }

    @Test
    void everyProblemTeachesAtLeastOneCommonMistake() {
        for (Problem p : problems) {
            assertThat(p.tests().stream().mapToInt(t -> t.mistakes().size()).sum())
                    .as("%s has no common mistakes", p.slug())
                    .isPositive();
        }
    }

    @Test
    void mistakesAreWellFormed() {
        for (Problem p : problems) {
            for (TestCase test : p.tests()) {
                for (Mistake m : test.mistakes()) {
                    assertThat(m.hint()).as(p.slug()).isNotBlank();
                    assertThat(m.output() == null || m.exception() == null)
                            .as("%s: a mistake matches an output or an exception, not both", p.slug()).isTrue();
                    if (m.output() != null) {
                        assertThat(m.output().strip()).as("%s: mistake equals the right answer", p.slug())
                                .isNotEqualTo(test.expected().strip());
                    }
                }
            }
        }
    }

    @Test
    void harnessMatchesMode() {
        for (Problem p : problems) {
            if (p.mode() == Mode.METHOD) {
                assertThat(p.harness()).as(p.slug()).contains("new Solution()");
                assertThat(p.starterCode()).as(p.slug()).contains("class Solution");
            } else {
                assertThat(p.harness()).as(p.slug()).isNull();
            }
        }
    }
}
