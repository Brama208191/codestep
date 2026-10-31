package com.codestep.judge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProgramEntryTest {

    @Test
    void usesPublicMainClass() {
        ProgramEntry entry = ProgramEntry.detect("public class Main { public static void main(String[] a) {} }");

        assertThat(entry.fileName()).isEqualTo("Main.java");
        assertThat(entry.mainClass()).isEqualTo("Main");
    }

    @Test
    void supportsAnyPublicClassName() {
        ProgramEntry entry = ProgramEntry.detect("public class Solver { public static void main(String[] a) {} }");

        assertThat(entry.fileName()).isEqualTo("Solver.java");
        assertThat(entry.mainClass()).isEqualTo("Solver");
    }

    @Test
    void findsMainInNonPublicClassAfterHelpers() {
        String code = """
                class Helper { static int twice(int x) { return x * 2; } }
                class App {
                    public static void main(String[] args) { }
                }
                """;

        ProgramEntry entry = ProgramEntry.detect(code);

        assertThat(entry.fileName()).isEqualTo("App.java");
        assertThat(entry.mainClass()).isEqualTo("App");
    }

    @Test
    void publicClassDecidesFileNameEvenWhenMainIsElsewhere() {
        String code = """
                class Runner { public static void main(String[] args) { } }
                public final class Library { }
                """;

        ProgramEntry entry = ProgramEntry.detect(code);

        assertThat(entry.fileName()).isEqualTo("Library.java");
        assertThat(entry.mainClass()).isEqualTo("Runner");
    }

    @Test
    void fallsBackToMainWhenNothingMatches() {
        ProgramEntry entry = ProgramEntry.detect("// nothing here yet");

        assertThat(entry.fileName()).isEqualTo("Main.java");
        assertThat(entry.mainClass()).isEqualTo("Main");
    }
}
