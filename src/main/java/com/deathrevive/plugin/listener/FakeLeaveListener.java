package com.deathrevive.plugin.listener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import com.deathrevive.plugin.disguise.ActiveDisguiseRegistry;
import com.deathrevive.plugin.disguise.PlayerDisguiseService;
import com.deathrevive.plugin.message.MessageService;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Handles death, quit, and join events to implement the fake-leave mechanic. On death the
 * real death message is suppressed and replaced with a configurable "{name} left the game"
 * broadcast; the player is held in spectator at their death location until revived via
 * {@link com.deathrevive.plugin.command.ReviveCommand}.
 */
public class FakeLeaveListener implements Listener {

    private final JavaPlugin plugin;
    private final ActiveDisguiseRegistry activeDisguiseRegistry;
    private final PlayerDisguiseService playerDisguiseService;
    private final Map<UUID, BukkitTask> pendingRespawnTasks = new HashMap<>();
    private final Set<UUID> fakedOutPlayers = new HashSet<>();
    private final MessageService messageService;

    public FakeLeaveListener(JavaPlugin plugin, ActiveDisguiseRegistry activeDisguiseRegistry,
                              PlayerDisguiseService playerDisguiseService, MessageService messageService) {
        this.plugin = plugin;
        this.activeDisguiseRegistry = activeDisguiseRegistry;
        this.playerDisguiseService = playerDisguiseService;
        this.messageService = messageService;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location deathLocation = player.getLocation();
        UUID playerId = player.getUniqueId();
        Optional<String> activeFakeName = activeDisguiseRegistry.getFakeName(playerId);
        Component originalDeathMessage = event.deathMessage();
        boolean killMessageEnabled = plugin.getConfig().getBoolean("kill-message.enabled", false);

        if (originalDeathMessage != null && killMessageEnabled) {
            String raw = PlainTextComponentSerializer.plainText().serialize(originalDeathMessage);

            String victimFake = activeFakeName.orElse(null);
            if (victimFake != null) {
                raw = raw.replace(player.getName(), victimFake);
            }

            Player killer = player.getKiller();
            if (killer != null) {
                String killerFake = activeDisguiseRegistry.getFakeName(killer.getUniqueId()).orElse(null);
                if (killerFake != null) {
                    raw = raw.replace(killer.getName(), killerFake);
                }
            }

            Bukkit.broadcast(Component.text(raw));
        } else if (activeFakeName.isEmpty() && originalDeathMessage != null) {
            Bukkit.broadcast(originalDeathMessage);
        }

        event.deathMessage(null);
        String displayName = activeFakeName.orElseGet(player::getName);
        Bukkit.broadcast(Component.text(
                messageService.get("events.death.fake-leave", "name", displayName), NamedTextColor.YELLOW));

        for (Player nearby : player.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(deathLocation) <= 48 * 48) {
                nearby.playSound(deathLocation, Sound.ENTITY_WITHER_DEATH, 0.8f, 1.0f);
            }
        }

        if (activeFakeName.isPresent()) {
            activeDisguiseRegistry.clear(playerId);
            playerDisguiseService.remove(player);
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

    // HIGHEST stellt sicher dass wir die Quit-Message als letztes nullen,
    // nachdem andere Plugins sie ggf. gesetzt haben.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        if (fakedOutPlayers.contains(playerId)) {
            event.quitMessage(null);
        }

        BukkitTask pendingRespawnTask = pendingRespawnTasks.remove(playerId);
        if (pendingRespawnTask != null) {
            pendingRespawnTask.cancel();
            fakedOutPlayers.remove(playerId);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        activeDisguiseRegistry.getIdentity(player.getUniqueId())
                .ifPresent(identity -> playerDisguiseService.apply(player, identity));
    }

    /** @return {@code true} if the player is currently in fake-out state (dead, awaiting revive). */
    public boolean isFakedOut(UUID playerId) {
        return fakedOutPlayers.contains(playerId);
    }

    /**
     * Removes the player from fake-out state. Called by
     * {@link com.deathrevive.plugin.command.ReviveCommand} on successful revive.
     */
    public void clearFakedOut(UUID playerId) {
        fakedOutPlayers.remove(playerId);
    }
}
