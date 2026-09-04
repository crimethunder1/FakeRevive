package com.fakerevive.armor;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks which players currently have their armor locked in place, mimicking Curse of Binding
 * without touching the items themselves. Locks are keyed by UUID and persisted in
 * {@code armor-lock.yml} inside the plugin's data folder, so they survive logouts and restarts.
 * The actual enforcement lives in {@link ArmorLockListener}.
 */
public class ArmorLockManager {

    private final JavaPlugin plugin;
    private final File lockFile;
    private final YamlConfiguration lockConfig = new YamlConfiguration();
    private final Set<UUID> lockedPlayers = new LinkedHashSet<>();

    public ArmorLockManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.lockFile = new File(plugin.getDataFolder(), "armor-lock.yml");
    }

    /**
     * Loads locked player UUIDs from {@code armor-lock.yml}. Safe to call multiple times; clears
     * the in-memory set first. A missing file is treated as "nobody is locked".
     */
    public void loadLocks() {
        lockedPlayers.clear();
        if (!lockFile.exists()) {
            return;
        }

        try {
            lockConfig.load(lockFile);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning("Failed to load armor-lock.yml: " + e.getMessage());
            return;
        }

        for (String uuid : lockConfig.getStringList("locked")) {
            try {
                lockedPlayers.add(UUID.fromString(uuid));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Skipping invalid UUID in armor-lock.yml: " + uuid);
            }
        }
    }

    /** @return {@code true} if the player wasn't locked before and is now. */
    public boolean lock(UUID playerId) {
        if (!lockedPlayers.add(playerId)) {
            return false;
        }
        save();
        return true;
    }

    /** @return {@code true} if the player was locked before and no longer is. */
    public boolean unlock(UUID playerId) {
        if (!lockedPlayers.remove(playerId)) {
            return false;
        }
        save();
        return true;
    }

    public boolean isLocked(UUID playerId) {
        return lockedPlayers.contains(playerId);
    }

    /** @return a snapshot of all locked UUIDs; modifications do not affect the registry. */
    public Set<UUID> getLocked() {
        return new LinkedHashSet<>(lockedPlayers);
    }

    private void save() {
        List<String> serialized = new ArrayList<>(lockedPlayers.size());
        for (UUID playerId : lockedPlayers) {
            serialized.add(playerId.toString());
        }
        lockConfig.set("locked", serialized);

        try {
            plugin.getDataFolder().mkdirs();
            lockConfig.save(lockFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save armor-lock.yml: " + e.getMessage());
        }
    }
}
