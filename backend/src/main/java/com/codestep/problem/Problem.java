package com.codestep.problem;

import java.util.List;

public record Problem(
        String slug,
        String title,
        String topic,
        String pattern,
        int order,
        int difficulty,
        Mode mode,
        String description,
        String starterCode,
        String harness,
        List<String> hints,
        WorkedExample workedExample,
        List<TraceStep> walkthrough,
        List<TestCase> tests
) {

    public List<TestCase> sampleTests() {
        return tests.stream().filter(TestCase::sample).toList();
    }
}
