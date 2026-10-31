package com.codestep.judge;

public record SubmissionResult(
        Verdict verdict,
        int passed,
        int total,
        FailedCase failedCase,
        String compileOutput,
        String explanation,
        boolean commonMistake,
        long runtimeMs
) {

    public record FailedCase(int number, boolean sample, String input, String expected, String actual, String stderr) {
    }

    static SubmissionResult accepted(int total, long runtimeMs) {
        return new SubmissionResult(Verdict.ACCEPTED, total, total, null, null, null, false, runtimeMs);
    }

    static SubmissionResult compileError(String compileOutput, String explanation, long runtimeMs) {
        return new SubmissionResult(Verdict.COMPILE_ERROR, 0, 0, null, compileOutput, explanation, false, runtimeMs);
    }

    static SubmissionResult failed(Verdict verdict, int passed, int total, FailedCase failedCase,
                                   String explanation, long runtimeMs) {
        return failed(verdict, passed, total, failedCase, explanation, false, runtimeMs);
    }

    static SubmissionResult failed(Verdict verdict, int passed, int total, FailedCase failedCase,
                                   String explanation, boolean commonMistake, long runtimeMs) {
        return new SubmissionResult(verdict, passed, total, failedCase, null, explanation, commonMistake, runtimeMs);
    }

    public static SubmissionResult internalError(String explanation) {
        return new SubmissionResult(Verdict.INTERNAL_ERROR, 0, 0, null, null, explanation, false, 0);
    }
}
