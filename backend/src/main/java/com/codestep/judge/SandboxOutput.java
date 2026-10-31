package com.codestep.judge;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public record SandboxOutput(boolean compiled, String compileOutput, List<TestRun> runs) {

    public record TestRun(int exitCode, String stdout, String stderr) {
    }

    static SandboxOutput parse(String raw) {
        List<String> lines = raw.lines().toList();
        int i = 0;
        while (i < lines.size() && !lines.get(i).equals("COMPILED") && !lines.get(i).equals("COMPILE_ERROR")) {
            i++;
        }
        if (i == lines.size() || lines.contains("SANDBOX_ERROR")) {
            throw new IllegalStateException("Sandbox produced no result: " + raw);
        }
        if (lines.get(i).equals("COMPILE_ERROR")) {
            return new SandboxOutput(false, decode(lineAt(lines, i + 1)), List.of());
        }

        List<TestRun> runs = new ArrayList<>();
        for (i = i + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("TEST ")) {
                int exitCode = Integer.parseInt(line.split(" ")[2]);
                runs.add(new TestRun(exitCode, decode(lineAt(lines, i + 1)), decode(lineAt(lines, i + 2))));
                i += 2;
            }
        }
        return new SandboxOutput(true, null, runs);
    }

    private static String lineAt(List<String> lines, int index) {
        return index < lines.size() ? lines.get(index) : "";
    }

    private static String decode(String base64) {
        return new String(Base64.getDecoder().decode(base64.trim()), StandardCharsets.UTF_8);
    }
}
