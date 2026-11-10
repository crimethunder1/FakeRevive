package com.fakerevive.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public class FakedOutPlayerStore {
    private static final String PLAYERS_KEY = "players";

    private final YamlFileStore store;
    private final Logger logger;
    private final Set<UUID> fakedOutPlayers = new LinkedHashSet<>();

    public FakedOutPlayerStore(Path file, Logger logger) {
        this.store = new YamlFileStore(file, logger);
        this.logger = logger;
    }

    public void load() {
        fakedOutPlayers.clear();
        YamlConfiguration configuration = store.load();
        for (String playerIdText : configuration.getStringList(PLAYERS_KEY)) {
            try {
                fakedOutPlayers.add(UUID.fromString(playerIdText));
            } catch (IllegalArgumentException exception) {
                logger.warning("Skipping invalid UUID in faked-out.yml: " + playerIdText);
            }
        }
    }

    public boolean contains(UUID playerId) {
        return fakedOutPlayers.contains(playerId);
    }

    public boolean add(UUID playerId) {
        if (!fakedOutPlayers.add(playerId)) {
            return false;
        }
        save();
        return true;
    }

    public boolean remove(UUID playerId) {
        if (!fakedOutPlayers.remove(playerId)) {
            return false;
        }
        save();
        return true;
    }

    public Set<UUID> getFakedOutPlayers() {
        return new LinkedHashSet<>(fakedOutPlayers);
    }

    private void save() {
        List<String> playerIdTexts = new ArrayList<>();
        for (UUID playerId : fakedOutPlayers) {
            playerIdTexts.add(playerId.toString());
        }

        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set(PLAYERS_KEY, playerIdTexts);
        store.save(configuration);
    }
}
