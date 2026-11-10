package com.fakerevive.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class YamlFileStoreTest {
    private static final String BROKEN_CONTENT = "kits: [unclosed\n  - : :\n";

    @Test
    void missingFileLoadsAsEmptyConfigurationWithoutWarning(@TempDir Path directory) {
        RecordingLogger logger = new RecordingLogger();
        YamlFileStore store = new YamlFileStore(directory.resolve("data.yml"), logger.logger());

        YamlConfiguration configuration = store.load();

        assertTrue(configuration.getKeys(true).isEmpty());
        assertTrue(logger.warnings().isEmpty());
        assertFalse(Files.exists(directory.resolve("data.yml.broken")));
    }

    @Test
    void brokenFileIsCopiedToBrokenFileAndWarningIsLogged(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("data.yml");
        Files.writeString(file, BROKEN_CONTENT, StandardCharsets.UTF_8);
        RecordingLogger logger = new RecordingLogger();
        YamlFileStore store = new YamlFileStore(file, logger.logger());

        YamlConfiguration configuration = store.load();

        assertTrue(configuration.getKeys(true).isEmpty());
        assertEquals(BROKEN_CONTENT, Files.readString(directory.resolve("data.yml.broken"), StandardCharsets.UTF_8));
        assertEquals(1, logger.warnings().size());
        assertTrue(logger.warnings().get(0).contains("data.yml"));
    }

    @Test
    void savingAfterBrokenLoadKeepsTheBackupAndReplacesTheBrokenFile(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("data.yml");
        Files.writeString(file, BROKEN_CONTENT, StandardCharsets.UTF_8);
        YamlFileStore store = new YamlFileStore(file, new RecordingLogger().logger());
        store.load();

        YamlConfiguration replacement = new YamlConfiguration();
        replacement.set("value", 7);
        assertTrue(store.save(replacement));

        assertEquals(BROKEN_CONTENT, Files.readString(directory.resolve("data.yml.broken"), StandardCharsets.UTF_8));
        assertEquals(7, store.load().getInt("value"));
    }

    @Test
    void saveWritesAtomicallyAndLeavesNoTemporaryFile(@TempDir Path directory) {
        Path file = directory.resolve("nested").resolve("data.yml");
        YamlFileStore store = new YamlFileStore(file, new RecordingLogger().logger());
        YamlConfiguration first = new YamlConfiguration();
        first.set("value", "first");
        YamlConfiguration second = new YamlConfiguration();
        second.set("value", "second");

        assertTrue(store.save(first));
        assertTrue(store.save(second));

        assertEquals("second", store.load().getString("value"));
        assertFalse(Files.exists(file.resolveSibling("data.yml.tmp")));
    }

    @Test
    void saveReturnsFalseAndLogsWhenTheTargetCannotBeWritten(@TempDir Path directory) throws IOException {
        Path blockingFile = directory.resolve("blocker");
        Files.writeString(blockingFile, "not a directory", StandardCharsets.UTF_8);
        RecordingLogger logger = new RecordingLogger();
        YamlFileStore store = new YamlFileStore(blockingFile.resolve("data.yml"), logger.logger());

        assertFalse(store.save(new YamlConfiguration()));
        assertEquals(1, logger.warnings().size());
    }

    public static final class RecordingLogger {
        private final Logger logger = Logger.getAnonymousLogger();
        private final List<String> warnings = new ArrayList<>();

        public RecordingLogger() {
            logger.setUseParentHandlers(false);
            logger.addHandler(new Handler() {
                @Override
                public void publish(LogRecord record) {
                    if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                        warnings.add(record.getMessage());
                    }
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            });
        }

        public Logger logger() {
            return logger;
        }

        public List<String> warnings() {
            return warnings;
        }
    }
}
