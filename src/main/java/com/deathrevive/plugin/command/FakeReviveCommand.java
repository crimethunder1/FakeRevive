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
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class FakeReviveCommand implements CommandExecutor, TabCompleter {

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

    public FakeReviveCommand(JavaPlugin plugin, FakeLeaveListener fakeLeaveListener, FakeNamePool fakeNamePool,
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
        if (args.length == 0) {
            sender.sendMessage(Component.text(messageService.get("commands.fr.usage"), NamedTextColor.RED));
            return true;
        }

        String[] rest = Arrays.copyOfRange(args, 1, args.length);

        switch (args[0].toLowerCase()) {
            case "revive":
                return handleRevive(sender, rest);
            case "undisguise":
                return handleUndisguise(sender, rest);
            case "disguise":
                return handleDisguise(sender, rest);
            case "kit":
                return handleKit(sender, rest);
            case "reload":
                return handleReload(sender);
            case "help":
                return handleHelp(sender);
            default:
                sender.sendMessage(Component.text(messageService.get("commands.fr.usage"), NamedTextColor.RED));
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("fakerevive.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return filterByPrefix(List.of("revive", "undisguise", "disguise", "kit", "reload", "help"), args[0]);
        }

        return switch (args[0].toLowerCase()) {
            case "revive" -> completeRevive(args);
            case "undisguise" -> completeUndisguise(args);
            case "disguise" -> completeDisguise(args);
            case "kit" -> completeKit(args);
            default -> List.of();
        };
    }

    private List<String> completeRevive(String[] args) {
        if (args.length == 2) {
            List<String> options = new ArrayList<>();
            options.add("@a");
            Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
            return filterByPrefix(options, args[1]);
        }
        if (args.length == 3) {
            return filterByPrefix(new ArrayList<>(kitManager.getKitNames()), args[2]);
        }
        return List.of();
    }

    private List<String> completeUndisguise(String[] args) {
        if (args.length == 2) {
            return onlinePlayerNames(args[1]);
        }
        return List.of();
    }

    private List<String> completeDisguise(String[] args) {
        if (args.length == 2) {
            return onlinePlayerNames(args[1]);
        }
        if (args.length == 3) {
            return filterByPrefix(new ArrayList<>(kitManager.getKitNames()), args[2]);
        }
        return List.of();
    }

    private List<String> completeKit(String[] args) {
        if (args.length == 2) {
            return filterByPrefix(List.of("save", "list", "give", "equip"), args[1]);
        }
        if (args.length >= 3) {
            return switch (args[1].toLowerCase()) {
                case "give", "equip" -> {
                    if (args.length == 3) {
                        yield filterByPrefix(new ArrayList<>(kitManager.getKitNames()), args[2]);
                    }
                    if (args.length == 4) {
                        yield onlinePlayerNames(args[3]);
                    }
                    yield List.of();
                }
                default -> List.of();
            };
        }
        return List.of();
    }

    private List<String> onlinePlayerNames(String prefix) {
        List<String> names = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
        return filterByPrefix(names, prefix);
    }

    private List<String> filterByPrefix(List<String> options, String prefix) {
        String lowerPrefix = prefix.toLowerCase();
        return options.stream()
                .filter(option -> option.toLowerCase().startsWith(lowerPrefix))
                .collect(Collectors.toList());
    }

    private boolean handleRevive(CommandSender sender, String[] args) {
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

    private boolean handleUndisguise(CommandSender sender, String[] args) {
        Player target;
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(Component.text(messageService.get("commands.undisguise.usage"), NamedTextColor.RED));
                return true;
            }
            target = (Player) sender;
        } else {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text(messageService.get("commands.undisguise.not-found"), NamedTextColor.RED));
                return true;
            }
        }

        if (activeDisguiseRegistry.getIdentity(target.getUniqueId()).isEmpty()) {
            sender.sendMessage(Component.text(messageService.get("commands.undisguise.not-disguised"), NamedTextColor.RED));
            return true;
        }

        activeDisguiseRegistry.clear(target.getUniqueId());
        playerDisguiseService.remove(target);
        target.sendMessage(Component.text(messageService.get("commands.undisguise.success-self"), NamedTextColor.YELLOW));

        if (sender != target) {
            sender.sendMessage(Component.text(
                    messageService.get("commands.undisguise.success-other", "player", target.getName()),
                    NamedTextColor.GREEN));
        }

        return true;
    }

    private boolean handleDisguise(CommandSender sender, String[] args) {
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage(Component.text(messageService.get("commands.disguise.usage"), NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text(messageService.get("commands.disguise.not-found"), NamedTextColor.RED));
            return true;
        }

        String kitName = args.length == 2 ? args[1] : null;

        activeDisguiseRegistry.clear(target.getUniqueId());

        Optional<FakeIdentity> identity = fakeNamePool.assignRandomIdentity();
        if (identity.isEmpty()) {
            sender.sendMessage(Component.text(messageService.get("commands.disguise.pool-exhausted"), NamedTextColor.RED));
            return true;
        }

        FakeIdentity fakeIdentity = identity.get();
        activeDisguiseRegistry.assign(target.getUniqueId(), fakeIdentity);
        playerDisguiseService.apply(target, fakeIdentity);

        String fakeName = fakeIdentity.name();
        target.sendMessage(Component.text(
                messageService.get("disguise.applied", "name", fakeName), NamedTextColor.YELLOW));
        target.sendActionBar(Component.text(
                messageService.get("disguise.applied-actionbar", "name", fakeName), NamedTextColor.YELLOW));

        if (!sender.equals(target)) {
            sender.sendMessage(Component.text(
                    messageService.get("commands.disguise.success", "player", target.getName(), "name", fakeName),
                    NamedTextColor.GREEN));
        }

        if (kitName != null) {
            PlayerInventory inventory = target.getInventory();
            inventory.clear();
            inventory.setArmorContents(null);
            inventory.setItemInOffHand(null);
            target.updateInventory();
            if (!kitManager.equipKit(kitName, target)) {
                logger.warning("Kit \"" + kitName + "\" not found — player " + target.getName()
                        + " disguised without a kit.");
            }
        }

        replenishPool();
        return true;
    }

    private boolean handleKit(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text(messageService.get("commands.kit.usage"), NamedTextColor.RED));
            return true;
        }

        if (args[0].equalsIgnoreCase("save")) {
            if (args.length != 2) {
                sender.sendMessage(Component.text(messageService.get("commands.kit.usage"), NamedTextColor.RED));
                return true;
            }

            if (!(sender instanceof Player)) {
                sender.sendMessage(Component.text(messageService.get("commands.kit.players-only"), NamedTextColor.RED));
                return true;
            }

            String name = args[1];
            kitManager.saveKit(name, (Player) sender);
            sender.sendMessage(Component.text(messageService.get("commands.kit.saved", "name", name), NamedTextColor.GREEN));
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            if (args.length != 1) {
                sender.sendMessage(Component.text(messageService.get("commands.kit.usage"), NamedTextColor.RED));
                return true;
            }

            Set<String> kitNames = kitManager.getKitNames();
            if (kitNames.isEmpty()) {
                sender.sendMessage(Component.text(messageService.get("commands.kit.list-empty"), NamedTextColor.YELLOW));
            } else {
                sender.sendMessage(Component.text(
                        messageService.get("commands.kit.list-format", "kits", String.join(", ", kitNames)),
                        NamedTextColor.GREEN));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            return handleKitGive(sender, args);
        }

        if (args[0].equalsIgnoreCase("equip")) {
            return handleKitEquip(sender, args);
        }

        sender.sendMessage(Component.text(messageService.get("commands.kit.usage"), NamedTextColor.RED));
        return true;
    }

    private boolean handleKitGive(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(Component.text(messageService.get("commands.kit.give-usage"), NamedTextColor.RED));
            return true;
        }

        String name = args[1];
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(Component.text(
                    messageService.get("commands.kit.give-player-not-found", "player", args[2]), NamedTextColor.RED));
            return true;
        }

        if (plugin.getConfig().getBoolean("kits.clear-before-give", false)) {
            target.getInventory().clear();
        }

        if (!kitManager.giveKit(name, target)) {
            sender.sendMessage(Component.text(
                    messageService.get("commands.kit.give-kit-not-found", "name", name), NamedTextColor.RED));
            return true;
        }

        sender.sendMessage(Component.text(
                messageService.get("commands.kit.give-success", "name", name, "player", target.getName()),
                NamedTextColor.GREEN));
        return true;
    }

    private boolean handleKitEquip(CommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage(Component.text(messageService.get("commands.kit.equip-usage"), NamedTextColor.RED));
            return true;
        }

        String name = args[1];
        Player target;
        boolean equippingOther;
        if (args.length == 3) {
            target = Bukkit.getPlayer(args[2]);
            equippingOther = true;
            if (target == null) {
                sender.sendMessage(Component.text(
                        messageService.get("commands.kit.equip-player-not-found", "player", args[2]), NamedTextColor.RED));
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(Component.text(messageService.get("commands.kit.players-only"), NamedTextColor.RED));
                return true;
            }
            target = (Player) sender;
            equippingOther = false;
        }

        if (plugin.getConfig().getBoolean("kits.clear-before-equip", false)) {
            PlayerInventory inventory = target.getInventory();
            inventory.clear();
            inventory.setArmorContents(null);
            inventory.setItemInOffHand(null);
        }

        if (!kitManager.equipKit(name, target)) {
            sender.sendMessage(Component.text(
                    messageService.get("commands.kit.equip-kit-not-found", "name", name), NamedTextColor.RED));
            return true;
        }

        if (equippingOther) {
            sender.sendMessage(Component.text(
                    messageService.get("commands.kit.equip-success-other", "name", name, "player", target.getName()),
                    NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text(
                    messageService.get("commands.kit.equip-success", "name", name), NamedTextColor.GREEN));
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        plugin.reloadConfig();
        messageService.reload(plugin);
        kitManager.loadKits();
        sender.sendMessage(Component.text(
                messageService.get("commands.reload.success"), NamedTextColor.GREEN));
        return true;
    }

    private boolean handleHelp(CommandSender sender) {
        sender.sendMessage(Component.text(messageService.get("commands.help.header"), NamedTextColor.GOLD));
        sender.sendMessage(Component.text(messageService.get("commands.help.revive"), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(messageService.get("commands.help.undisguise"), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(messageService.get("commands.help.disguise"), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(messageService.get("commands.help.kit-save"), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(messageService.get("commands.help.kit-list"), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(messageService.get("commands.help.kit-give"), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(messageService.get("commands.help.kit-equip"), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(messageService.get("commands.help.reload"), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(messageService.get("commands.help.help"), NamedTextColor.YELLOW));
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
            if (!kitManager.equipKit(kitName, player)) {
                logger.warning("Kit \"" + kitName + "\" not found — player " + player.getName()
                        + " was revived without a kit.");
            }
        }
    }

    /**
     * Fetches one fresh name+skin identity from the real Mojang API to replace the one just
     * handed out, keeping the pool topped up over time. Runs off the main thread since it makes
     * blocking network calls; only the final pool update is hopped back onto the main thread.
     * Skips starting a new fetch while one is already running, so e.g. `/fr revive @a` on a large
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
