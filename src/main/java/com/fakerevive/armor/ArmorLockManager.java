package com.fakerevive.armor;

import com.fakerevive.storage.YamlFileStore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ArmorLockManager {
    private final JavaPlugin plugin;
    private final YamlFileStore store;
    private final Set<UUID> lockedPlayers = new LinkedHashSet<>();

    public ArmorLockManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.store = new YamlFileStore(plugin.getDataFolder().toPath().resolve("armor-lock.yml"), plugin.getLogger());
    }

    public void loadLocks() {
        lockedPlayers.clear();
        YamlConfiguration lockConfig = store.load();

        for (String uniqueId : lockConfig.getStringList("locked")) {
            try {
                lockedPlayers.add(UUID.fromString(uniqueId));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Skipping invalid UUID in armor-lock.yml: " + uniqueId);
            }
        }
    }

    public boolean lock(UUID playerId) {
        if (!lockedPlayers.add(playerId)) {
            return false;
        }
        save();
        return true;
    }

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

    public Set<UUID> getLocked() {
        return new LinkedHashSet<>(lockedPlayers);
    }

    private void save() {
        List<String> serialized = new ArrayList<>(lockedPlayers.size());
        for (UUID playerId : lockedPlayers) {
            serialized.add(playerId.toString());
        }
        YamlConfiguration lockConfig = new YamlConfiguration();
        lockConfig.set("locked", serialized);

        store.save(lockConfig);
    }
}
