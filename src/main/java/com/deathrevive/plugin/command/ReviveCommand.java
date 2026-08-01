package com.deathrevive.plugin.command;

import com.deathrevive.plugin.disguise.ActiveDisguiseRegistry;
import com.deathrevive.plugin.disguise.FakeIdentity;
import com.deathrevive.plugin.disguise.FakeNamePool;
import com.deathrevive.plugin.disguise.MojangIdentityFetcher;
import com.deathrevive.plugin.disguise.PlayerDisguiseService;
import com.deathrevive.plugin.kit.KitManager;
import com.deathrevive.plugin.listener.FakeLeaveListener;
import com.deathrevive.plugin.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class ReviveCommand implements CommandExecutor {

    private static final int REPLENISH_MAX_ATTEMPTS = 25;

    private final JavaPlugin plugin;
    private final FakeLeaveListener fakeLeaveListener;
    private final FakeNamePool fakeNamePool;
    private final ActiveDisguiseRegistry activeDisguiseRegistry;
    private final PlayerDisguiseService playerDisguiseService;
    private final MojangIdentityFetcher identityFetcher;
    private final KitManager kitManager;
    private final Logger logger;
    private final MessageService messageService;
    private final AtomicBoolean replenishInProgress = new AtomicBoolean(false);

    public ReviveCommand(JavaPlugin plugin, FakeLeaveListener fakeLeaveListener, FakeNamePool fakeNamePool,
                          ActiveDisguiseRegistry activeDisguiseRegistry, PlayerDisguiseService playerDisguiseService,
                          MojangIdentityFetcher identityFetcher, KitManager kitManager, Logger logger,
                          MessageService messageService) {
        this.plugin = plugin;
        this.fakeLeaveListener = fakeLeaveListener;
        this.fakeNamePool = fakeNamePool;
        this.activeDisguiseRegistry = activeDisguiseRegistry;
        this.playerDisguiseService = playerDisguiseService;
        this.identityFetcher = identityFetcher;
        this.kitManager = kitManager;
        this.logger = logger;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage(Component.text(messageService.get("commands.revive.usage"), NamedTextColor.RED));
            return true;
        }

        String kitName = args.length == 2 ? args[1] : null;

        Location spawnLocation;
        if (sender instanceof Player) {
            spawnLocation = ((Player) sender).getLocation();
        } else {
            spawnLocation = Bukkit.getWorlds().get(0).getSpawnLocation();
        }

        if (args[0].equalsIgnoreCase("@a")) {
            int revivedCount = 0;

            for (Player target : Bukkit.getOnlinePlayers()) {
                if (fakeLeaveListener.isFakedOut(target.getUniqueId())) {
                    revivePlayer(target, spawnLocation, kitName);
                    revivedCount++;
                }
            }

            if (revivedCount == 0) {
                sender.sendMessage(Component.text(
                        messageService.get("commands.revive.no-targets"),
                        NamedTextColor.YELLOW));
            } else {
                sender.sendMessage(Component.text(
                        messageService.get("commands.revive.success-multiple", "count", String.valueOf(revivedCount)),
                        NamedTextColor.GREEN));
            }
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);

        if (target == null) {
            sender.sendMessage(Component.text(messageService.get("commands.revive.not-found"), NamedTextColor.RED));
            return true;
        }

        if (!fakeLeaveListener.isFakedOut(target.getUniqueId())) {
            sender.sendMessage(Component.text(messageService.get("commands.revive.not-in-spectator"), NamedTextColor.RED));
            return true;
        }

        revivePlayer(target, spawnLocation, kitName);
        sender.sendMessage(Component.text(
                messageService.get("commands.revive.success-single", "player", target.getName()),
                NamedTextColor.GREEN));
        return true;
    }

    private void revivePlayer(Player player, Location location, @Nullable String kitName) {
        fakeLeaveListener.clearFakedOut(player.getUniqueId());
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(location);

        Optional<FakeIdentity> identity = fakeNamePool.assignRandomIdentity();
        if (identity.isPresent()) {
            FakeIdentity fakeIdentity = identity.get();
            activeDisguiseRegistry.assign(player.getUniqueId(), fakeIdentity);
            playerDisguiseService.apply(player, fakeIdentity);

            String fakeName = fakeIdentity.name();
            player.sendMessage(Component.text(
                    messageService.get("disguise.applied", "name", fakeName), NamedTextColor.YELLOW));
            player.sendActionBar(Component.text(
                    messageService.get("disguise.applied-actionbar", "name", fakeName), NamedTextColor.YELLOW));

            replenishPool();
        } else {
            logger.warning(messageService.get("events.pool-exhausted", "player", player.getName()));
        }

        if (kitName != null) {
            PlayerInventory inventory = player.getInventory();
            inventory.clear();
            inventory.setArmorContents(null);
            inventory.setItemInOffHand(null);
            player.updateInventory();
            kitManager.equipKit(kitName, player);
        }
    }

    /**
     * Fetches one fresh name+skin identity from the real Mojang API to replace the one just
     * handed out, keeping the pool topped up over time. Runs off the main thread since it makes
     * blocking network calls; only the final pool update is hopped back onto the main thread.
     * Skips starting a new fetch while one is already running, so e.g. `/revive @a` on a large
     * group doesn't fire a burst of parallel requests at Mojang - the next revive after this one
     * finishes will trigger the following top-up.
     */
    private void replenishPool() {
        if (!replenishInProgress.compareAndSet(false, true)) {
            return;
        }

        Set<String> knownNames = fakeNamePool.getKnownNames();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                identityFetcher.fetchNewIdentity(knownNames, REPLENISH_MAX_ATTEMPTS)
                        .ifPresent(identity -> Bukkit.getScheduler().runTask(plugin, () -> fakeNamePool.offer(identity)));
            } finally {
                replenishInProgress.set(false);
            }
        });
    }
}
