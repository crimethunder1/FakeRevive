package com.fakerevive.disguise;

import com.fakerevive.kit.KitManager;
import com.fakerevive.message.MessageService;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DisguiseCoordinator {
    private static final int ARMOR_SLOTS = 4;

    private final JavaPlugin plugin;
    private final FakeNamePool fakeNamePool;
    private final FakeNamePoolReplenisher replenisher;
    private final ActiveDisguiseRegistry activeDisguiseRegistry;
    private final PlayerDisguiseService playerDisguiseService;
    private final MojangIdentityFetcher identityFetcher;
    private final KitManager kitManager;
    private final MessageService messageService;
    private final Logger logger;

    public DisguiseCoordinator(JavaPlugin plugin, FakeNamePool fakeNamePool, FakeNamePoolReplenisher replenisher,
                               ActiveDisguiseRegistry activeDisguiseRegistry,
                               PlayerDisguiseService playerDisguiseService, MojangIdentityFetcher identityFetcher,
                               KitManager kitManager, MessageService messageService, Logger logger) {
        this.plugin = plugin;
        this.fakeNamePool = fakeNamePool;
        this.replenisher = replenisher;
        this.activeDisguiseRegistry = activeDisguiseRegistry;
        this.playerDisguiseService = playerDisguiseService;
        this.identityFetcher = identityFetcher;
        this.kitManager = kitManager;
        this.messageService = messageService;
        this.logger = logger;
    }

    public Optional<FakeIdentity> assignFromPool(Player player) {
        Optional<FakeIdentity> identity = fakeNamePool.assignRandomIdentity();
        replenisher.requestTopUp();

        if (identity.isEmpty()) {
            logger.warning(messageService.get("events.pool-exhausted", "player", player.getName()));
            return identity;
        }

        applyIdentity(player, identity.get());
        return identity;
    }

    public void applyIdentity(Player player, FakeIdentity fakeIdentity) {
        activeDisguiseRegistry.assign(player.getUniqueId(), fakeIdentity);
        playerDisguiseService.apply(player, fakeIdentity);

        String fakeName = fakeIdentity.name();
        messageService.send(player, "disguise.applied", NamedTextColor.YELLOW, "name", fakeName);
        player.sendActionBar(messageService.prefixed(
                "disguise.applied-actionbar", NamedTextColor.YELLOW, "name", fakeName));
    }

    public void remove(Player player) {
        activeDisguiseRegistry.clear(player.getUniqueId());
        playerDisguiseService.remove(player);
    }

    public boolean isDisguised(Player player) {
        return activeDisguiseRegistry.getIdentity(player.getUniqueId()).isPresent();
    }

    public void applyKit(Player player, String kitName) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[ARMOR_SLOTS]);
        inventory.setItemInOffHand(null);
        player.updateInventory();
        if (!kitManager.equipKit(kitName, player)) {
            logger.warning("Kit \"" + kitName + "\" not found for player " + player.getName());
        }
    }

    public void fetchIdentityAsynchronously(CommandSender resultTarget, String minecraftName,
                                            Consumer<Optional<FakeIdentity>> mainThreadHandler) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Optional<FakeIdentity> fetchedIdentity;
            try {
                fetchedIdentity = identityFetcher.fetchIdentityByName(minecraftName);
            } catch (RuntimeException exception) {
                reportUnexpectedFailure(resultTarget, exception);
                return;
            }
            try {
                Bukkit.getScheduler().runTask(plugin,
                        () -> runGuarded(resultTarget, () -> mainThreadHandler.accept(fetchedIdentity)));
            } catch (RuntimeException exception) {
                logger.log(Level.WARNING, "Could not hand the fetched identity back to the main thread", exception);
            }
        });
    }

    private void runGuarded(CommandSender resultTarget, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Unexpected failure while applying a fetched identity", exception);
            sendUnexpectedError(resultTarget);
        }
    }

    private void reportUnexpectedFailure(CommandSender resultTarget, RuntimeException failure) {
        logger.log(Level.WARNING, "Unexpected failure while fetching an identity", failure);
        try {
            Bukkit.getScheduler().runTask(plugin, () -> sendUnexpectedError(resultTarget));
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Could not report the failure to the command sender", exception);
        }
    }

    private void sendUnexpectedError(CommandSender resultTarget) {
        messageService.send(resultTarget, "commands.fr.unexpected-error", NamedTextColor.RED);
    }
}
