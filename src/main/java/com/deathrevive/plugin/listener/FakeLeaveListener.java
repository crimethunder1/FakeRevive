package com.deathrevive.plugin.listener;

import com.deathrevive.plugin.disguise.ActiveDisguiseRegistry;
import com.deathrevive.plugin.disguise.PlayerDisguiseService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class FakeLeaveListener implements Listener {

    private final JavaPlugin plugin;
    private final ActiveDisguiseRegistry activeDisguiseRegistry;
    private final PlayerDisguiseService playerDisguiseService;
    private final Map<UUID, BukkitTask> pendingRespawnTasks = new HashMap<>();
    private final Set<UUID> fakedOutPlayers = new HashSet<>();

    public FakeLeaveListener(JavaPlugin plugin, ActiveDisguiseRegistry activeDisguiseRegistry,
                              PlayerDisguiseService playerDisguiseService) {
        this.plugin = plugin;
        this.activeDisguiseRegistry = activeDisguiseRegistry;
        this.playerDisguiseService = playerDisguiseService;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location deathLocation = player.getLocation();
        UUID playerId = player.getUniqueId();
        Optional<String> activeFakeName = activeDisguiseRegistry.getFakeName(playerId);
        Component originalDeathMessage = event.deathMessage();

        if (activeFakeName.isEmpty() && originalDeathMessage != null) {
            Bukkit.broadcast(originalDeathMessage);
        }

        event.deathMessage(null);
        String displayName = activeFakeName.orElseGet(player::getName);
        Bukkit.broadcast(Component.text(displayName + " left the game", NamedTextColor.YELLOW));

        if (activeFakeName.isPresent()) {
            playerDisguiseService.remove(player);
            activeDisguiseRegistry.clear(playerId);
        }

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

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        activeDisguiseRegistry.getIdentity(player.getUniqueId())
                .ifPresent(identity -> playerDisguiseService.apply(player, identity));
    }

    public boolean isFakedOut(UUID playerId) {
        return fakedOutPlayers.contains(playerId);
    }

    public void clearFakedOut(UUID playerId) {
        fakedOutPlayers.remove(playerId);
    }
}
