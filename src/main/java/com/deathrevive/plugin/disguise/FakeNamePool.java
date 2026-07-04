package com.deathrevive.plugin.disguise;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

public class FakeNamePool {

    private static final String USED_NAMES_KEY = "used-names";
    private static final Gson GSON = new Gson();
    private static final Type NAME_LIST_TYPE = new TypeToken<List<String>>() {
    }.getType();

    private final List<String> availableNames;
    private final Set<String> usedNames;
    private final Path usedNamesFile;
    private final Random random;

    public FakeNamePool(InputStream namesResource, Path usedNamesFile) {
        this(namesResource, usedNamesFile, new Random());
    }

    FakeNamePool(InputStream namesResource, Path usedNamesFile, Random random) {
        this.usedNamesFile = usedNamesFile;
        this.usedNames = new LinkedHashSet<>(readUsedNames(usedNamesFile));
        this.availableNames = readNames(namesResource);
        this.availableNames.removeAll(this.usedNames);
        this.random = random;
    }

    public Optional<String> assignRandomName() {
        if (availableNames.isEmpty()) {
            return Optional.empty();
        }

        int index = random.nextInt(availableNames.size());
        int lastIndex = availableNames.size() - 1;
        String name = availableNames.get(index);
        availableNames.set(index, availableNames.get(lastIndex));
        availableNames.remove(lastIndex);

        usedNames.add(name);
        persistUsedNames();
        return Optional.of(name);
    }

    private static List<String> readNames(InputStream namesResource) {
        try (InputStreamReader reader = new InputStreamReader(namesResource, StandardCharsets.UTF_8)) {
            List<String> names = GSON.fromJson(reader, NAME_LIST_TYPE);
            return names != null ? new ArrayList<>(names) : new ArrayList<>();
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
