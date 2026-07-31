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

    private static final String NAMES_JSON = "["
            + "{\"name\": \"Alpha_Wolf\", \"skinValue\": \"v1\", \"skinSignature\": \"s1\"}, "
            + "{\"name\": \"Beta_Falcon\", \"skinValue\": \"v2\", \"skinSignature\": \"s2\"}, "
            + "{\"name\": \"Gamma_Tiger\", \"skinValue\": \"v3\", \"skinSignature\": \"s3\"}"
            + "]";

    @Test
    void assignsEveryNameAtMostOnce(@TempDir Path tempDir) {
        FakeNamePool pool = new FakeNamePool(namesStream(), tempDir.resolve("used.yml"), new Random(1));

        Set<String> assigned = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            Optional<FakeIdentity> identity = pool.assignRandomIdentity();
            assertTrue(identity.isPresent());
            assertTrue(assigned.add(identity.get().name()), "Name was assigned twice: " + identity.get().name());
        }
    }

    @Test
    void returnsEmptyWhenPoolIsExhausted(@TempDir Path tempDir) {
        FakeNamePool pool = new FakeNamePool(namesStream(), tempDir.resolve("used.yml"), new Random(1));

        for (int i = 0; i < 3; i++) {
            pool.assignRandomIdentity();
        }

        assertEquals(Optional.empty(), pool.assignRandomIdentity());
    }

    @Test
    void parsesPrettyPrintedMultilineJsonAsProducedByTheGenerator(@TempDir Path tempDir) {
        String prettyJson = "[\n"
                + "  {\"name\": \"Alpha_Wolf\", \"skinValue\": \"v1\", \"skinSignature\": \"s1\"},\n"
                + "  {\"name\": \"Beta_Falcon\", \"skinValue\": \"v2\", \"skinSignature\": \"s2\"},\n"
                + "  {\"name\": \"Gamma_Tiger\", \"skinValue\": \"v3\", \"skinSignature\": \"s3\"}\n"
                + "]\n";
        InputStream stream = new ByteArrayInputStream(prettyJson.getBytes(StandardCharsets.UTF_8));
        FakeNamePool pool = new FakeNamePool(stream, tempDir.resolve("used.yml"), new Random(1));

        Set<String> assigned = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            assigned.add(pool.assignRandomIdentity().orElseThrow().name());
        }

        assertEquals(Set.of("Alpha_Wolf", "Beta_Falcon", "Gamma_Tiger"), assigned);
    }

    @Test
    void neverReassignsNamesPersistedFromAnEarlierProcess(@TempDir Path tempDir) {
        Path usedNamesFile = tempDir.resolve("used.yml");

        FakeNamePool firstRun = new FakeNamePool(namesStream(), usedNamesFile, new Random(1));
        String firstName = firstRun.assignRandomIdentity().orElseThrow().name();

        FakeNamePool secondRun = new FakeNamePool(namesStream(), usedNamesFile, new Random(1));
        String secondName = secondRun.assignRandomIdentity().orElseThrow().name();
        String thirdName = secondRun.assignRandomIdentity().orElseThrow().name();

        assertTrue(!secondName.equals(firstName) && !thirdName.equals(firstName));
        assertEquals(Optional.empty(), secondRun.assignRandomIdentity());
    }

    private static InputStream namesStream() {
        return new ByteArrayInputStream(NAMES_JSON.getBytes(StandardCharsets.UTF_8));
    }
}
