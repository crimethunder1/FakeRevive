package com.deathrevive.plugin.command;

import com.deathrevive.plugin.disguise.ActiveDisguiseRegistry;
import com.deathrevive.plugin.disguise.FakeIdentity;
import com.deathrevive.plugin.disguise.FakeNamePool;
import com.deathrevive.plugin.disguise.MojangIdentityFetcher;
import com.deathrevive.plugin.disguise.PlayerDisguiseService;
import com.deathrevive.plugin.listener.FakeLeaveListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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
    private final Logger logger;
    private final AtomicBoolean replenishInProgress = new AtomicBoolean(false);

    public ReviveCommand(JavaPlugin plugin, FakeLeaveListener fakeLeaveListener, FakeNamePool fakeNamePool,
                          ActiveDisguiseRegistry activeDisguiseRegistry, PlayerDisguiseService playerDisguiseService,
                          MojangIdentityFetcher identityFetcher, Logger logger) {
        this.plugin = plugin;
        this.fakeLeaveListener = fakeLeaveListener;
        this.fakeNamePool = fakeNamePool;
        this.activeDisguiseRegistry = activeDisguiseRegistry;
        this.playerDisguiseService = playerDisguiseService;
        this.identityFetcher = identityFetcher;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage(Component.text("Benutzung: /revive <Spieler> oder /revive @a", NamedTextColor.RED));
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
                        "Es gab keine Spieler im Spectator-Modus, die wiederbelebt werden konnten.",
                        NamedTextColor.YELLOW));
            } else {
                sender.sendMessage(Component.text("Es wurden erfolgreich ", NamedTextColor.GREEN)
                        .append(Component.text(revivedCount, NamedTextColor.YELLOW))
                        .append(Component.text(" Spieler wiederbelebt!", NamedTextColor.GREEN)));
            }
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);

        if (target == null) {
            sender.sendMessage(Component.text("Dieser Spieler wurde nicht gefunden.", NamedTextColor.RED));
            return true;
        }

        if (!fakeLeaveListener.isFakedOut(target.getUniqueId())) {
            sender.sendMessage(Component.text("Dieser Spieler ist nicht im Spectator-Modus!", NamedTextColor.RED));
            return true;
        }

        revivePlayer(target, spawnLocation, kitName);
        sender.sendMessage(Component.text("Du hast " + target.getName() + " erfolgreich wiederbelebt!", NamedTextColor.GREEN));
        return true;
    }

    private void revivePlayer(Player player, Location location, @Nullable String kitName) {
        fakeLeaveListener.clearFakedOut(player.getUniqueId());
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(location);

        Optional<FakeIdentity> identity = fakeNamePool.assignRandomIdentity();
        if (identity.isPresent()) {
            activeDisguiseRegistry.assign(player.getUniqueId(), identity.get());
            playerDisguiseService.apply(player, identity.get());

            String fakeName = identity.get().name();
            player.sendMessage(Component.text("Du wurdest als \"" + fakeName + "\" verkleidet.", NamedTextColor.YELLOW));
            player.sendActionBar(Component.text("Verkleidet als: " + fakeName, NamedTextColor.YELLOW));

            replenishPool();
        } else {
            logger.warning("Fake-Namen-Pool ist erschöpft, " + player.getName()
                    + " wird ohne neue Verkleidung wiederbelebt.");
        }

        if (kitName != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "clear " + player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "skit give " + player.getName() + " " + kitName);
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
