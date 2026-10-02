package com.fakerevive.exempt;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ExemptManager {

    public static final String PERMISSION_EXEMPT = "fakerevive.exempt";
    private static final String CONFIG_KEY = "exempt-players";
    private static final String FILE_SECTION = "players";

    private final JavaPlugin plugin;
    private final File exemptFile;
    private final Map<UUID, String> storedPlayers = new LinkedHashMap<>();
    private final Map<String, String> configuredNames = new LinkedHashMap<>();

    public ExemptManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.exemptFile = new File(plugin.getDataFolder(), "exempt-players.yml");
    }

    public void load() {
        loadConfiguredNames();
        loadStoredPlayers();
    }

    private void loadConfiguredNames() {
        configuredNames.clear();
        for (String name : plugin.getConfig().getStringList(CONFIG_KEY)) {
            String trimmedName = name.trim();
            if (!trimmedName.isEmpty()) {
                configuredNames.putIfAbsent(normalize(trimmedName), trimmedName);
            }
        }
    }

    private void loadStoredPlayers() {
        storedPlayers.clear();
        if (!exemptFile.exists()) {
            return;
        }

        YamlConfiguration exemptConfig = new YamlConfiguration();
        try {
            exemptConfig.load(exemptFile);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().warning("Failed to load exempt-players.yml: " + exception.getMessage());
            return;
        }

        ConfigurationSection section = exemptConfig.getConfigurationSection(FILE_SECTION);
        if (section == null) {
            return;
        }

        for (String playerIdText : section.getKeys(false)) {
            try {
                storedPlayers.put(UUID.fromString(playerIdText), section.getString(playerIdText, playerIdText));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Skipping invalid UUID in exempt-players.yml: " + playerIdText);
            }
        }
    }

    public boolean add(UUID playerId, String playerName) {
        if (storedPlayers.containsKey(playerId)) {
            return false;
        }
        storedPlayers.put(playerId, playerName);
        save();
        return true;
    }

    public boolean remove(UUID playerId) {
        if (storedPlayers.remove(playerId) == null) {
            return false;
        }
        save();
        return true;
    }

    public boolean isStored(UUID playerId) {
        return storedPlayers.containsKey(playerId);
    }

    public Optional<UUID> findStoredByName(String playerName) {
        return storedPlayers.entrySet().stream()
                .filter(entry -> entry.getValue().equalsIgnoreCase(playerName))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public Map<UUID, String> getStoredPlayers() {
        return new LinkedHashMap<>(storedPlayers);
    }

    public boolean isConfigured(String playerName) {
        return configuredNames.containsKey(normalize(playerName));
    }

    public List<String> getConfiguredNames() {
        return new ArrayList<>(configuredNames.values());
    }

    public boolean isExempt(Player player) {
        return storedPlayers.containsKey(player.getUniqueId())
                || isConfigured(player.getName())
                || player.hasPermission(PERMISSION_EXEMPT);
    }

    private void save() {
        YamlConfiguration exemptConfig = new YamlConfiguration();
        ConfigurationSection section = exemptConfig.createSection(FILE_SECTION);
        for (Map.Entry<UUID, String> entry : storedPlayers.entrySet()) {
            section.set(entry.getKey().toString(), entry.getValue());
        }

        try {
            plugin.getDataFolder().mkdirs();
            exemptConfig.save(exemptFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save exempt-players.yml: " + exception.getMessage());
        }
    }

    private static String normalize(String playerName) {
        return playerName.toLowerCase(Locale.ROOT);
    }
}
