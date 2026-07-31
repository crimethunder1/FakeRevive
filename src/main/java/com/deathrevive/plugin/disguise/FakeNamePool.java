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
    private static final Type IDENTITY_LIST_TYPE = new TypeToken<List<FakeIdentity>>() {
    }.getType();

    private final List<FakeIdentity> availableIdentities;
    private final Set<String> usedNames;
    private final Path usedNamesFile;
    private final Random random;

    public FakeNamePool(InputStream namesResource, Path usedNamesFile) {
        this(namesResource, usedNamesFile, new Random());
    }

    FakeNamePool(InputStream namesResource, Path usedNamesFile, Random random) {
        this.usedNamesFile = usedNamesFile;
        this.usedNames = new LinkedHashSet<>(readUsedNames(usedNamesFile));
        this.availableIdentities = readIdentities(namesResource);
        this.availableIdentities.removeIf(identity -> usedNames.contains(identity.name()));
        this.random = random;
    }

    /**
     * Adds a freshly fetched identity to the pool so it can be handed out by a later
     * {@link #assignRandomIdentity()} call. Not thread-safe - callers must invoke this on the
     * same thread as the rest of the pool's API (the server main thread).
     */
    public void offer(FakeIdentity identity) {
        if (!usedNames.contains(identity.name())) {
            availableIdentities.add(identity);
        }
    }

    /**
     * @return every name currently known to the pool (used or still available), so a caller
     *         fetching a replacement identity can avoid suggesting a duplicate.
     */
    public Set<String> getKnownNames() {
        Set<String> known = new LinkedHashSet<>(usedNames);
        for (FakeIdentity identity : availableIdentities) {
            known.add(identity.name());
        }
        return known;
    }

    public Optional<FakeIdentity> assignRandomIdentity() {
        if (availableIdentities.isEmpty()) {
            return Optional.empty();
        }

        int index = random.nextInt(availableIdentities.size());
        int lastIndex = availableIdentities.size() - 1;
        FakeIdentity identity = availableIdentities.get(index);
        availableIdentities.set(index, availableIdentities.get(lastIndex));
        availableIdentities.remove(lastIndex);

        usedNames.add(identity.name());
        persistUsedNames();
        return Optional.of(identity);
    }

    private static List<FakeIdentity> readIdentities(InputStream namesResource) {
        try (InputStreamReader reader = new InputStreamReader(namesResource, StandardCharsets.UTF_8)) {
            List<FakeIdentity> identities = GSON.fromJson(reader, IDENTITY_LIST_TYPE);
            return identities != null ? new ArrayList<>(identities) : new ArrayList<>();
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
