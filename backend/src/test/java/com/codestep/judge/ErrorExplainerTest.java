package com.codestep.judge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorExplainerTest {

    private final ErrorExplainer explainer = new ErrorExplainer();

    @Test
    void explainsArrayIndexOutOfBoundsWithUserLine() {
        String stderr = """
                Exception in thread "main" java.lang.ArrayIndexOutOfBoundsException: Index 3 out of bounds for length 3
                \tat Solution.sum(Solution.java:4)
                \tat Main.main(Main.java:11)
                """;

        String explanation = explainer.explainRuntime(stderr);

        assertThat(explanation)
                .contains("ArrayIndexOutOfBoundsException")
                .contains("i < nums.length")
                .contains("line 4 of your code");
    }

    @Test
    void reportsLineOfMainForFullPrograms() {
        String stderr = """
                Exception in thread "main" java.lang.NullPointerException
                \tat Main.main(Main.java:7)
                """;

        assertThat(explainer.explainRuntime(stderr)).contains("NullPointerException").contains("line 7");
    }

    @Test
    void explainsMissingMainMethod() {
        assertThat(explainer.explainRuntime("Error: Main method not found in class App"))
                .contains("public static void main");
    }

    @Test
    void fallsBackForUnknownExceptions() {
        assertThat(explainer.explainRuntime("Exception in thread \"main\" com.example.WeirdException"))
                .contains("WeirdException")
                .contains("Read the message");
    }

    @Test
    void explainsCommonCompileErrors() {
        assertThat(explainer.explainCompile("Solution.java:3: error: ';' expected")).contains("semicolon");
        assertThat(explainer.explainCompile("error: missing return statement")).contains("return");
        assertThat(explainer.explainCompile("error: cannot find symbol")).contains("typo");
        assertThat(explainer.explainCompile("error: something new")).contains("first error message");
    }

    @Test
    void stripsSandboxPathsFromCompilerOutput() {
        assertThat(explainer.cleanCompileOutput("/tmp/work/src/Solution.java:1: error"))
                .isEqualTo("Solution.java:1: error");
    }
}
