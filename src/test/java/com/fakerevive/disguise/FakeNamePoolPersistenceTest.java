package com.fakerevive.disguise;

import com.fakerevive.storage.YamlFileStoreTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeNamePoolPersistenceTest {
    private static final String NAMES_JSON = "["
            + "{\"name\": \"Alpha_Wolf\", \"skinValue\": \"v1\", \"skinSignature\": \"s1\"}, "
            + "{\"name\": \"Beta_Falcon\", \"skinValue\": \"v2\", \"skinSignature\": \"s2\"}"
            + "]";

    @Test
    void assignsIdentityEvenWhenPersistingFails(@TempDir Path directory) throws IOException {
        Path blockingFile = directory.resolve("blocker");
        Files.writeString(blockingFile, "not a directory", StandardCharsets.UTF_8);
        YamlFileStoreTest.RecordingLogger logger = new YamlFileStoreTest.RecordingLogger();
        FakeNamePool pool = new FakeNamePool(namesStream(), blockingFile.resolve("used.yml"), new Random(1), logger.logger());

        Optional<FakeIdentity> first = pool.assignRandomIdentity();
        Optional<FakeIdentity> second = pool.assignRandomIdentity();

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        assertEquals(2, pool.usedCount());
        assertEquals(0, pool.availableCount());
        assertEquals(2, logger.warnings().size());
    }

    @Test
    void brokenUsedNamesFileIsBackedUpInsteadOfOverwritten(@TempDir Path directory) throws IOException {
        Path usedNamesFile = directory.resolve("used.yml");
        String brokenContent = "used-names: [unclosed\n";
        Files.writeString(usedNamesFile, brokenContent, StandardCharsets.UTF_8);
        YamlFileStoreTest.RecordingLogger logger = new YamlFileStoreTest.RecordingLogger();
        FakeNamePool pool = new FakeNamePool(namesStream(), usedNamesFile, new Random(1), logger.logger());

        pool.assignRandomIdentity();

        assertEquals(brokenContent, Files.readString(directory.resolve("used.yml.broken"), StandardCharsets.UTF_8));
        assertEquals(1, logger.warnings().size());
    }

    private static InputStream namesStream() {
        return new ByteArrayInputStream(NAMES_JSON.getBytes(StandardCharsets.UTF_8));
    }
}
