package com.fakerevive.command;

import com.fakerevive.disguise.ActiveDisguiseRegistry;
import com.fakerevive.team.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class TargetResolver {
    public static final String ALL_SELECTOR = "@a";

    private final TeamManager teamManager;
    private final ActiveDisguiseRegistry activeDisguiseRegistry;

    public TargetResolver(TeamManager teamManager, ActiveDisguiseRegistry activeDisguiseRegistry) {
        this.teamManager = teamManager;
        this.activeDisguiseRegistry = activeDisguiseRegistry;
    }

    public boolean isAllSelector(String input) {
        return input.equalsIgnoreCase(ALL_SELECTOR);
    }

    public List<Player> resolveAllOnline() {
        return new ArrayList<>(Bukkit.getOnlinePlayers());
    }

    public boolean isTeam(String input) {
        return teamManager.teamExists(input);
    }

    public List<UUID> resolveTeamMemberIds(String teamName) {
        return new ArrayList<>(teamManager.getTeamMembers(teamName));
    }

    public List<Player> resolveOnlineTeamMembers(String teamName) {
        List<Player> members = new ArrayList<>();
        for (UUID memberId : teamManager.getTeamMembers(teamName)) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                members.add(member);
            }
        }
        return members;
    }

    public Optional<Player> resolvePlayer(String name) {
        return Optional.ofNullable(Bukkit.getPlayer(name));
    }

    public Optional<Player> resolvePlayerOrFakeName(String name) {
        Player byName = Bukkit.getPlayer(name);
        if (byName != null) {
            return Optional.of(byName);
        }
        return activeDisguiseRegistry.findByFakeName(name).map(Bukkit::getPlayer);
    }

    public List<String> completionOptions(boolean includeGroups, boolean includeFakeNames) {
        List<String> options = new ArrayList<>();
        if (includeGroups) {
            options.add(ALL_SELECTOR);
            options.addAll(teamManager.getTeamNames());
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            options.add(online.getName());
            if (includeFakeNames) {
                activeDisguiseRegistry.getFakeName(online.getUniqueId()).ifPresent(options::add);
            }
        }
        return options;
    }
}
