package com.fakerevive.disguise;

import com.fakerevive.storage.YamlFileStore;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

public class FakeNamePool {
    private static final String USED_NAMES_KEY = "used-names";
    private static final Gson GSON = new Gson();
    private static final Type IDENTITY_LIST_TYPE = new TypeToken<List<FakeIdentity>>() {
    }.getType();

    private final List<FakeIdentity> availableIdentities;
    private final Set<String> availableNames = new LinkedHashSet<>();
    private final Set<String> usedNames;
    private final YamlFileStore usedNamesStore;
    private final Random random;

    public FakeNamePool(InputStream namesResource, Path usedNamesFile) {
        this(namesResource, usedNamesFile, new Random(), Logger.getLogger(FakeNamePool.class.getName()));
    }

    public FakeNamePool(InputStream namesResource, Path usedNamesFile, Logger logger) {
        this(namesResource, usedNamesFile, new Random(), logger);
    }

    FakeNamePool(InputStream namesResource, Path usedNamesFile, Random random) {
        this(namesResource, usedNamesFile, random, Logger.getLogger(FakeNamePool.class.getName()));
    }

    FakeNamePool(InputStream namesResource, Path usedNamesFile, Random random, Logger logger) {
        this.usedNamesStore = new YamlFileStore(usedNamesFile, logger);
        this.usedNames = new LinkedHashSet<>(usedNamesStore.load().getStringList(USED_NAMES_KEY));
        this.availableIdentities = readIdentities(namesResource);
        this.availableIdentities.removeIf(identity -> usedNames.contains(identity.name()));
        for (FakeIdentity identity : availableIdentities) {
            availableNames.add(identity.name());
        }
        this.random = random;
    }

    public boolean offer(FakeIdentity identity) {
        if (usedNames.contains(identity.name()) || !availableNames.add(identity.name())) {
            return false;
        }
        availableIdentities.add(identity);
        return true;
    }

    public Set<String> getKnownNames() {
        Set<String> known = new LinkedHashSet<>(usedNames);
        known.addAll(availableNames);
        return known;
    }

    public int availableCount() {
        return availableIdentities.size();
    }

    public int usedCount() {
        return usedNames.size();
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

        availableNames.remove(identity.name());
        usedNames.add(identity.name());
        persistUsedNames();
        return Optional.of(identity);
    }

    private static List<FakeIdentity> readIdentities(InputStream namesResource) {
        try (InputStreamReader reader = new InputStreamReader(namesResource, StandardCharsets.UTF_8)) {
            List<FakeIdentity> identities = GSON.fromJson(reader, IDENTITY_LIST_TYPE);
            return identities != null ? new ArrayList<>(identities) : new ArrayList<>();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void persistUsedNames() {
        YamlConfiguration config = new YamlConfiguration();
        config.set(USED_NAMES_KEY, new ArrayList<>(usedNames));
        usedNamesStore.save(config);
    }
}
