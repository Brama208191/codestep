package com.codestep.problem;

public record Mistake(String output, String exception, String hint) {

    public boolean matchesException(String stderr) {
        return exception != null && stderr.contains(exception);
    }

    public boolean matchesOutput(String normalizedOutput) {
        return exception == null && (output == null || output.strip().equals(normalizedOutput));
    }
}
