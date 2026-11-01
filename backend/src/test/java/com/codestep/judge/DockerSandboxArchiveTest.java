package com.codestep.judge;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DockerSandboxArchiveTest {

    @Test
    void packsSourcesAndNumberedTestInputs() throws IOException {
        byte[] archive = DockerSandbox.archive(Map.of("Solution.java", "class Solution {}"), "Main", 2, List.of("1\n", "2\n"));

        Map<String, String> entries = read(archive);

        assertThat(entries).containsKeys("src/", "tests/");
        assertThat(entries).containsEntry("main", "Main").containsEntry("limit", "2");
        assertThat(entries).containsEntry("src/Solution.java", "class Solution {}");
        assertThat(entries).containsEntry("tests/000.in", "1\n");
        assertThat(entries).containsEntry("tests/001.in", "2\n");
    }

    private static Map<String, String> read(byte[] archive) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (TarArchiveInputStream tar = new TarArchiveInputStream(new ByteArrayInputStream(archive))) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(tar.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
