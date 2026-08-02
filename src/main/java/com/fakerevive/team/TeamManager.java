package com.fakerevive.team;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Manages teams — named groups of players each optionally linked to a kit.
 * Persisted in {@code teams.yml} inside the plugin's data folder. A player can
 * belong to at most one team; adding them to a new team removes them from
 * the old one automatically.
 */
public class TeamManager {

    private final JavaPlugin plugin;
    private final File teamsFile;
    private final YamlConfiguration teamsConfig = new YamlConfiguration();
    private final Map<String, TeamData> teams = new LinkedHashMap<>();

    private static class TeamData {
        String kit;
        final Set<UUID> members = new LinkedHashSet<>();
    }

    public TeamManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.teamsFile = new File(plugin.getDataFolder(), "teams.yml");
    }

    /**
     * Loads teams from {@code teams.yml}. Safe to call multiple times; clears the
     * in-memory team list before reloading. A missing file is treated as an empty team list.
     */
    public void loadTeams() {
        teams.clear();
        if (!teamsFile.exists()) {
            return;
        }

        try {
            teamsConfig.load(teamsFile);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning("Failed to load teams.yml: " + e.getMessage());
            return;
        }

        ConfigurationSection teamsSection = teamsConfig.getConfigurationSection("teams");
        if (teamsSection == null) {
            return;
        }

        for (String name : teamsSection.getKeys(false)) {
            TeamData data = new TeamData();
            data.kit = teamsSection.getString(name + ".kit");
            for (String uuid : teamsSection.getStringList(name + ".members")) {
                try {
                    data.members.add(UUID.fromString(uuid));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Skipping invalid UUID \"" + uuid + "\" in team \"" + name + "\".");
                }
            }
            teams.put(name, data);
        }
    }

    /** Reloads teams from disk. Called on {@code /fr reload}. */
    public void reload() {
        loadTeams();
    }

    /** Creates a new empty team. No-op if the team already exists. */
    public void createTeam(String name) {
        teams.putIfAbsent(name, new TeamData());
        save();
    }

    /**
     * Deletes a team and removes all its members.
     * @return {@code true} if the team existed and was deleted.
     */
    public boolean deleteTeam(String name) {
        if (teams.remove(name) == null) {
            return false;
        }
        save();
        return true;
    }

    /**
     * Assigns a player to a team, removing them from any previous team first.
     * @return {@code false} if the named team does not exist.
     */
    public boolean addPlayer(UUID playerId, String teamName) {
        if (!teams.containsKey(teamName)) {
            return false;
        }
        teams.values().forEach(data -> data.members.remove(playerId));
        teams.get(teamName).members.add(playerId);
        save();
        return true;
    }

    /** Removes a player from whichever team they currently belong to. */
    public void removePlayer(UUID playerId) {
        teams.values().forEach(data -> data.members.remove(playerId));
        save();
    }

    /** @return the name of the team the player belongs to, or empty if none. */
    public Optional<String> getPlayerTeam(UUID playerId) {
        return teams.entrySet().stream()
                .filter(entry -> entry.getValue().members.contains(playerId))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /**
     * Assigns a kit to a team.
     * @return {@code false} if the team does not exist.
     */
    public boolean setTeamKit(String teamName, @Nullable String kitName) {
        TeamData data = teams.get(teamName);
        if (data == null) {
            return false;
        }
        data.kit = kitName;
        save();
        return true;
    }

    /** @return the kit assigned to this team, or empty if none. */
    public Optional<String> getTeamKit(String teamName) {
        return Optional.ofNullable(teams.get(teamName)).map(data -> data.kit);
    }

    /** @return a snapshot of all team names in insertion order. */
    public Set<String> getTeamNames() {
        return new LinkedHashSet<>(teams.keySet());
    }

    /** @return a snapshot of the UUIDs of all members of the given team, or an empty set. */
    public Set<UUID> getTeamMembers(String teamName) {
        TeamData data = teams.get(teamName);
        return data == null ? Set.of() : new LinkedHashSet<>(data.members);
    }

    /** @return {@code true} if a team with that name exists. */
    public boolean teamExists(String name) {
        return teams.containsKey(name);
    }

    private void save() {
        teamsConfig.set("teams", null);
        for (Map.Entry<String, TeamData> entry : teams.entrySet()) {
            String path = "teams." + entry.getKey();
            teamsConfig.set(path + ".kit", entry.getValue().kit);
            List<String> memberStrings = new ArrayList<>();
            entry.getValue().members.forEach(uuid -> memberStrings.add(uuid.toString()));
            teamsConfig.set(path + ".members", memberStrings);
        }

        try {
            plugin.getDataFolder().mkdirs();
            teamsConfig.save(teamsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save teams.yml: " + e.getMessage());
        }
    }
}
