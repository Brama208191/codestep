package com.codestep.judge;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SandboxOutputTest {

    private static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesCompileError() {
        SandboxOutput output = SandboxOutput.parse("COMPILE_ERROR\n" + b64("Solution.java:1: error: ';' expected") + "\n");

        assertThat(output.compiled()).isFalse();
        assertThat(output.compileOutput()).contains("';' expected");
        assertThat(output.runs()).isEmpty();
    }

    @Test
    void parsesTestRunsIncludingEmptyOutput() {
        String raw = "COMPILED\n"
                + "TEST 000 0\n" + b64("6\n") + "\n\n"
                + "TEST 001 1\n\n" + b64("Exception in thread \"main\" java.lang.NullPointerException") + "\n";

        SandboxOutput output = SandboxOutput.parse(raw);

        assertThat(output.compiled()).isTrue();
        assertThat(output.runs()).hasSize(2);
        assertThat(output.runs().get(0).exitCode()).isZero();
        assertThat(output.runs().get(0).stdout()).isEqualTo("6\n");
        assertThat(output.runs().get(0).stderr()).isEmpty();
        assertThat(output.runs().get(1).exitCode()).isEqualTo(1);
        assertThat(output.runs().get(1).stdout()).isEmpty();
        assertThat(output.runs().get(1).stderr()).contains("NullPointerException");
    }

    @Test
    void ignoresNoiseBeforeTheProtocol() {
        SandboxOutput output = SandboxOutput.parse("some docker warning\nCOMPILED\nTEST 000 0\n" + b64("1") + "\n\n");

        assertThat(output.runs()).hasSize(1);
    }

    @Test
    void rejectsOutputWithoutProtocol() {
        assertThatThrownBy(() -> SandboxOutput.parse("Unable to find image 'codestep-runner:latest' locally"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsSandboxErrors() {
        assertThatThrownBy(() -> SandboxOutput.parse("SANDBOX_ERROR\n"))
                .isInstanceOf(IllegalStateException.class);
    }
}
