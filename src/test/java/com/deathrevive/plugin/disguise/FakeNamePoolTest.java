package com.deathrevive.plugin.disguise;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeNamePoolTest {

    private static final String NAMES_JSON = "[\"Alpha_Wolf\", \"Beta_Falcon\", \"Gamma_Tiger\"]";

    @Test
    void assignsEveryNameAtMostOnce(@TempDir Path tempDir) {
        FakeNamePool pool = new FakeNamePool(namesStream(), tempDir.resolve("used.yml"), new Random(1));

        Set<String> assigned = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            Optional<String> name = pool.assignRandomName();
            assertTrue(name.isPresent());
            assertTrue(assigned.add(name.get()), "Name was assigned twice: " + name.get());
        }
    }

    @Test
    void returnsEmptyWhenPoolIsExhausted(@TempDir Path tempDir) {
        FakeNamePool pool = new FakeNamePool(namesStream(), tempDir.resolve("used.yml"), new Random(1));

        for (int i = 0; i < 3; i++) {
            pool.assignRandomName();
        }

        assertEquals(Optional.empty(), pool.assignRandomName());
    }

    @Test
    void parsesPrettyPrintedMultilineJsonAsProducedByTheGenerator(@TempDir Path tempDir) {
        String prettyJson = "[\n  \"Alpha_Wolf\",\n  \"Beta_Falcon\",\n  \"Gamma_Tiger\"\n]\n";
        InputStream stream = new ByteArrayInputStream(prettyJson.getBytes(StandardCharsets.UTF_8));
        FakeNamePool pool = new FakeNamePool(stream, tempDir.resolve("used.yml"), new Random(1));

        Set<String> assigned = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            assigned.add(pool.assignRandomName().orElseThrow());
        }

        assertEquals(Set.of("Alpha_Wolf", "Beta_Falcon", "Gamma_Tiger"), assigned);
    }

    @Test
    void neverReassignsNamesPersistedFromAnEarlierProcess(@TempDir Path tempDir) {
        Path usedNamesFile = tempDir.resolve("used.yml");

        FakeNamePool firstRun = new FakeNamePool(namesStream(), usedNamesFile, new Random(1));
        String firstName = firstRun.assignRandomName().orElseThrow();

        FakeNamePool secondRun = new FakeNamePool(namesStream(), usedNamesFile, new Random(1));
        String secondName = secondRun.assignRandomName().orElseThrow();
        String thirdName = secondRun.assignRandomName().orElseThrow();

        assertTrue(!secondName.equals(firstName) && !thirdName.equals(firstName));
        assertEquals(Optional.empty(), secondRun.assignRandomName());
    }

    private static InputStream namesStream() {
        return new ByteArrayInputStream(NAMES_JSON.getBytes(StandardCharsets.UTF_8));
    }
}
