package com.fakerevive.team;

import com.fakerevive.storage.EntryNameValidator;
import com.fakerevive.storage.YamlFileStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class TeamManager {
    private final JavaPlugin plugin;
    private final YamlFileStore store;
    private final Map<String, TeamData> teams = new LinkedHashMap<>();

    private static class TeamData {
        String kit;
        final Set<UUID> members = new LinkedHashSet<>();
    }

    public TeamManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.store = new YamlFileStore(plugin.getDataFolder().toPath().resolve("teams.yml"), plugin.getLogger());
    }

    public void loadTeams() {
        teams.clear();
        YamlConfiguration teamsConfig = store.load();

        ConfigurationSection teamsSection = teamsConfig.getConfigurationSection("teams");
        if (teamsSection == null) {
            return;
        }

        for (String name : teamsSection.getKeys(false)) {
            TeamData data = new TeamData();
            data.kit = teamsSection.getString(name + ".kit");
            for (String uniqueId : teamsSection.getStringList(name + ".members")) {
                try {
                    data.members.add(UUID.fromString(uniqueId));
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("Skipping invalid UUID \"" + uniqueId + "\" in team \"" + name + "\".");
                }
            }
            teams.put(name, data);
        }
    }

    public void reload() {
        loadTeams();
    }

    public void createTeam(String name) {
        if (!EntryNameValidator.isValid(name)) {
            throw new IllegalArgumentException("Invalid team name: " + name);
        }
        teams.putIfAbsent(name, new TeamData());
        save();
    }

    public boolean deleteTeam(String name) {
        if (teams.remove(name) == null) {
            return false;
        }
        save();
        return true;
    }

    public boolean addPlayer(UUID playerId, String teamName) {
        if (!teams.containsKey(teamName)) {
            return false;
        }
        teams.values().forEach(data -> data.members.remove(playerId));
        teams.get(teamName).members.add(playerId);
        save();
        return true;
    }

    public void removePlayer(UUID playerId) {
        teams.values().forEach(data -> data.members.remove(playerId));
        save();
    }

    public Optional<String> getPlayerTeam(UUID playerId) {
        return teams.entrySet().stream()
                .filter(entry -> entry.getValue().members.contains(playerId))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public boolean setTeamKit(String teamName, @Nullable String kitName) {
        TeamData data = teams.get(teamName);
        if (data == null) {
            return false;
        }
        data.kit = kitName;
        save();
        return true;
    }

    public Optional<String> getTeamKit(String teamName) {
        return Optional.ofNullable(teams.get(teamName)).map(data -> data.kit);
    }

    public Set<String> getTeamNames() {
        return new LinkedHashSet<>(teams.keySet());
    }

    public Set<UUID> getTeamMembers(String teamName) {
        TeamData data = teams.get(teamName);
        return data == null ? Set.of() : new LinkedHashSet<>(data.members);
    }

    public boolean teamExists(String name) {
        return teams.containsKey(name);
    }

    private void save() {
        YamlConfiguration teamsConfig = new YamlConfiguration();
        for (Map.Entry<String, TeamData> entry : teams.entrySet()) {
            String path = "teams." + entry.getKey();
            teamsConfig.set(path + ".kit", entry.getValue().kit);
            List<String> memberStrings = new ArrayList<>();
            entry.getValue().members.forEach(uniqueId -> memberStrings.add(uniqueId.toString()));
            teamsConfig.set(path + ".members", memberStrings);
        }

        store.save(teamsConfig);
    }
}
