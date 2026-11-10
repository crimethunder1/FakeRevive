package com.fakerevive.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.Permissible;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import com.fakerevive.disguise.ActiveDisguiseRegistry;
import com.fakerevive.disguise.DisguiseNameReplacer;
import com.fakerevive.disguise.FakeIdentity;
import com.fakerevive.disguise.PlayerDisguiseService;
import com.fakerevive.message.MessageService;
import com.fakerevive.storage.FakedOutPlayerStore;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class FakeLeaveListener implements Listener {
    private final JavaPlugin plugin;
    private final ActiveDisguiseRegistry activeDisguiseRegistry;
    private final PlayerDisguiseService playerDisguiseService;
    private final Map<UUID, BukkitTask> pendingRespawnTasks = new HashMap<>();
    private final FakedOutPlayerStore fakedOutPlayerStore;
    private final MessageService messageService;

    public FakeLeaveListener(JavaPlugin plugin, ActiveDisguiseRegistry activeDisguiseRegistry,
                              PlayerDisguiseService playerDisguiseService, FakedOutPlayerStore fakedOutPlayerStore,
                              MessageService messageService) {
        this.plugin = plugin;
        this.activeDisguiseRegistry = activeDisguiseRegistry;
        this.playerDisguiseService = playerDisguiseService;
        this.fakedOutPlayerStore = fakedOutPlayerStore;
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

        if (originalDeathMessage != null) {
            Component deathMessage = originalDeathMessage;

            if (activeFakeName.isPresent()) {
                deathMessage = DisguiseNameReplacer.replace(deathMessage, player.getName(), activeFakeName.get());
            }

            Player killer = player.getKiller();
            if (killer != null) {
                Optional<String> killerFake = activeDisguiseRegistry.getFakeName(killer.getUniqueId());
                if (killerFake.isPresent()) {
                    deathMessage = DisguiseNameReplacer.replace(deathMessage, killer.getName(), killerFake.get());
                }
            }

            if (killMessageEnabled) {
                Bukkit.broadcast(deathMessage);
            }
        }

        event.deathMessage(null);
        String displayName = activeFakeName.orElseGet(player::getName);
        Bukkit.broadcast(Component.text(
                messageService.get("events.death.fake-leave", "name", displayName), NamedTextColor.YELLOW));

        for (Player nearby : player.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(deathLocation) <= 48 * 48) {
                nearby.playSound(deathLocation, Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.0f);
            }
        }

        if (activeFakeName.isPresent()) {
            activeDisguiseRegistry.clear(playerId);
            playerDisguiseService.remove(player);
        }

        fakedOutPlayerStore.add(playerId);

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
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (fakedOutPlayerStore.contains(playerId)) {
            event.quitMessage(null);
        } else {
            Component quitMessage = event.quitMessage();
            Optional<String> fakeName = activeDisguiseRegistry.getFakeName(playerId);
            if (quitMessage != null && fakeName.isPresent()) {
                event.quitMessage(DisguiseNameReplacer.replace(quitMessage, player.getName(), fakeName.get()));
            }
        }

        BukkitTask pendingRespawnTask = pendingRespawnTasks.remove(playerId);
        if (pendingRespawnTask != null) {
            pendingRespawnTask.cancel();
            fakedOutPlayerStore.remove(playerId);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Optional<String> fakeName = activeDisguiseRegistry.getFakeName(player.getUniqueId());
        if (fakeName.isEmpty()) {
            return;
        }

        Component fakeDisplayName = Component.text(fakeName.get());
        Component realNamePrefix = Component.text("[" + player.getName() + "] ", NamedTextColor.AQUA);
        ChatRenderer previousRenderer = event.renderer();

        event.renderer((source, sourceDisplayName, message, viewer) -> {
            Component rendered = previousRenderer.render(source, fakeDisplayName, message, viewer);
            if (viewer instanceof Permissible permissible
                    && permissible.hasPermission(PlayerDisguiseService.SEE_REAL_NAME_PERMISSION)) {
                return Component.empty().append(realNamePrefix).append(rendered);
            }
            return rendered;
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (fakedOutPlayerStore.contains(player.getUniqueId())) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                event.joinMessage(null);
                return;
            }
            fakedOutPlayerStore.remove(player.getUniqueId());
        }

        Optional<FakeIdentity> identity = activeDisguiseRegistry.getIdentity(player.getUniqueId());
        if (identity.isEmpty()) {
            return;
        }

        playerDisguiseService.apply(player, identity.get());

        Component joinMessage = event.joinMessage();
        if (joinMessage != null) {
            event.joinMessage(DisguiseNameReplacer.replace(joinMessage, player.getName(), identity.get().name()));
        }
    }

    @EventHandler
    public void onPlayerAdvancementDone(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        Component advancementMessage = event.message();
        Optional<String> fakeName = activeDisguiseRegistry.getFakeName(player.getUniqueId());
        if (advancementMessage == null || fakeName.isEmpty()) {
            return;
        }

        event.message(DisguiseNameReplacer.replace(advancementMessage, player.getName(), fakeName.get()));
    }
}
