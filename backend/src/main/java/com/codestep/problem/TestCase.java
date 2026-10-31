package com.codestep.problem;

import java.util.List;
import java.util.Optional;

public record TestCase(String input, String expected, String display, boolean sample, List<Mistake> mistakes) {

    public TestCase(String input, String expected, String display, boolean sample) {
        this(input, expected, display, sample, List.of());
    }

    public TestCase {
        mistakes = mistakes == null ? List.of() : List.copyOf(mistakes);
    }

    public String displayInput() {
        return display != null ? display : input;
    }

    public Optional<Mistake> mistakeForOutput(String normalizedOutput) {
        return mistakes.stream().filter(m -> m.matchesOutput(normalizedOutput)).findFirst();
    }

    public Optional<Mistake> mistakeForError(String stderr) {
        return mistakes.stream().filter(m -> m.matchesException(stderr)).findFirst();
    }
}
