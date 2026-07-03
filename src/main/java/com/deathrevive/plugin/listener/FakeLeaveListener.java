package com.deathrevive.plugin.listener;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FakeLeaveListener implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, BukkitTask> pendingRespawnTasks = new HashMap<>();
    private final Set<UUID> fakedOutPlayers = new HashSet<>();

    public FakeLeaveListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location deathLocation = player.getLocation();
        UUID playerId = player.getUniqueId();
        String originalDeathMessage = event.getDeathMessage();

        if (originalDeathMessage != null) {
            Bukkit.broadcastMessage(originalDeathMessage);
        }

        event.setDeathMessage(null);
        Bukkit.broadcastMessage("§e" + player.getName() + " left the game");

        fakedOutPlayers.add(playerId);

        BukkitTask respawnTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingRespawnTasks.remove(playerId);

            if (!player.isOnline()) {
                return;
            }

            player.spigot().respawn();
            player.setGameMode(GameMode.SPECTATOR);
            player.teleport(deathLocation);
        }, 1L);

        pendingRespawnTasks.put(playerId, respawnTask);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        BukkitTask pendingRespawnTask = pendingRespawnTasks.remove(playerId);
        if (pendingRespawnTask != null) {
            pendingRespawnTask.cancel();
        }

        if (fakedOutPlayers.contains(playerId)) {
            event.quitMessage(null);
        }
    }

    public boolean isFakedOut(UUID playerId) {
        return fakedOutPlayers.contains(playerId);
    }

    public void clearFakedOut(UUID playerId) {
        fakedOutPlayers.remove(playerId);
    }
}
