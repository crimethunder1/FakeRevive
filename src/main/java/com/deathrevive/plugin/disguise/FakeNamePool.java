package com.deathrevive.plugin.disguise;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FakeNamePool {

    private static final String USED_NAMES_KEY = "used-names";
    private static final Pattern JSON_STRING = Pattern.compile("\"([^\"]*)\"");

    private final List<String> availableNames;
    private final Set<String> usedNames;
    private final Path usedNamesFile;
    private final Random random;

    public FakeNamePool(InputStream namesResource, Path usedNamesFile) {
        this(namesResource, usedNamesFile, new Random());
    }

    FakeNamePool(InputStream namesResource, Path usedNamesFile, Random random) {
        this.availableNames = readNames(namesResource);
        this.usedNamesFile = usedNamesFile;
        this.usedNames = new LinkedHashSet<>(readUsedNames(usedNamesFile));
        this.random = random;
    }

    public Optional<String> assignRandomName() {
        List<String> remaining = new ArrayList<>(availableNames);
        remaining.removeAll(usedNames);

        if (remaining.isEmpty()) {
            return Optional.empty();
        }

        String name = remaining.get(random.nextInt(remaining.size()));
        usedNames.add(name);
        persistUsedNames();
        return Optional.of(name);
    }

    private static List<String> readNames(InputStream namesResource) {
        try (InputStream stream = namesResource) {
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            List<String> names = new ArrayList<>();
            Matcher matcher = JSON_STRING.matcher(json);
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
            return names;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> readUsedNames(Path usedNamesFile) {
        if (!Files.exists(usedNamesFile)) {
            return List.of();
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(usedNamesFile.toFile());
        return config.getStringList(USED_NAMES_KEY);
    }

    private void persistUsedNames() {
        try {
            if (usedNamesFile.getParent() != null) {
                Files.createDirectories(usedNamesFile.getParent());
            }
            YamlConfiguration config = new YamlConfiguration();
            config.set(USED_NAMES_KEY, new ArrayList<>(usedNames));
            config.save(usedNamesFile.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
