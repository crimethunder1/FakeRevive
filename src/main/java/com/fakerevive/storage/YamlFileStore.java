package com.fakerevive.storage;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

public final class YamlFileStore {
    private final Path file;
    private final Logger logger;

    public YamlFileStore(Path file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public YamlConfiguration load() {
        YamlConfiguration configuration = new YamlConfiguration();
        if (!Files.exists(file)) {
            return configuration;
        }

        try {
            configuration.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
            return configuration;
        } catch (IOException | InvalidConfigurationException exception) {
            Path backupFile = backupFile();
            logger.warning("Failed to load " + fileName() + ": " + exception.getMessage()
                    + ". Starting empty and keeping a copy of the unreadable file at " + backupFile.getFileName() + ".");
            copyToBackup(backupFile);
            return new YamlConfiguration();
        }
    }

    public boolean save(YamlConfiguration configuration) {
        Path temporaryFile = file.resolveSibling(fileName() + ".tmp");
        try {
            Path directory = file.toAbsolutePath().getParent();
            if (directory != null) {
                Files.createDirectories(directory);
            }
            Files.writeString(temporaryFile, configuration.saveToString(), StandardCharsets.UTF_8);
            Files.move(temporaryFile, file, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException exception) {
            logger.warning("Failed to save " + fileName() + ": " + exception.getMessage());
            deleteQuietly(temporaryFile);
            return false;
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            return;
        }
    }

    private void copyToBackup(Path backupFile) {
        try {
            Files.copy(file, backupFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            logger.warning("Failed to back up " + fileName() + ": " + exception.getMessage());
        }
    }

    private Path backupFile() {
        return file.resolveSibling(fileName() + ".broken");
    }

    private String fileName() {
        return file.getFileName().toString();
    }
}
