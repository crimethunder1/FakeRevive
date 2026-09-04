package com.fakerevive.command;

import com.fakerevive.armor.ArmorLockManager;
import com.fakerevive.disguise.ActiveDisguiseRegistry;
import com.fakerevive.disguise.FakeIdentity;
import com.fakerevive.disguise.FakeNamePool;
import com.fakerevive.disguise.MojangIdentityFetcher;
import com.fakerevive.disguise.PlayerDisguiseService;
import com.fakerevive.kit.KitManager;
import com.fakerevive.listener.FakeLeaveListener;
import com.fakerevive.message.MessageService;
import com.fakerevive.team.TeamGui;
import com.fakerevive.team.TeamManager;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Handles all {@code /fr} subcommands: revive, undisguise, disguise, kit (save/list/give/equip/delete),
 * team, armor, leave, reload, and help. Also provides context-aware tab completion for all subcommands.
 */
public class FakeReviveCommand implements CommandExecutor, TabCompleter {

    private static final int REPLENISH_MAX_ATTEMPTS = 25;
    private static final int ARMOR_SLOTS = 4;
    private static final int NEARBY_RADIUS_SQUARED = 16 * 16;

    private final JavaPlugin plugin;
    private final FakeLeaveListener fakeLeaveListener;
    private final FakeNamePool fakeNamePool;
    private final ActiveDisguiseRegistry activeDisguiseRegistry;
    private final PlayerDisguiseService playerDisguiseService;
    private final MojangIdentityFetcher identityFetcher;
    private final KitManager kitManager;
    private final TeamManager teamManager;
    private final ArmorLockManager armorLockManager;
    private final TeamGui teamGui;
    private final Logger logger;
    private final MessageService messageService;
    private final AtomicBoolean replenishInProgress = new AtomicBoolean(false);
    private final Random random = new Random();
    public static final Component PREFIX = Component.text("[FakeRevive] ", NamedTextColor.AQUA);

    public FakeReviveCommand(JavaPlugin plugin, FakeLeaveListener fakeLeaveListener, FakeNamePool fakeNamePool,
                              ActiveDisguiseRegistry activeDisguiseRegistry, PlayerDisguiseService playerDisguiseService,
                              MojangIdentityFetcher identityFetcher, KitManager kitManager, TeamManager teamManager,
                              ArmorLockManager armorLockManager, TeamGui teamGui, Logger logger,
                              MessageService messageService) {
        this.plugin = plugin;
        this.fakeLeaveListener = fakeLeaveListener;
        this.fakeNamePool = fakeNamePool;
        this.activeDisguiseRegistry = activeDisguiseRegistry;
        this.playerDisguiseService = playerDisguiseService;
        this.identityFetcher = identityFetcher;
        this.kitManager = kitManager;
        this.teamManager = teamManager;
        this.armorLockManager = armorLockManager;
        this.teamGui = teamGui;
        this.logger = logger;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.fr.usage"), NamedTextColor.RED)));
            return true;
        }

        if (args[0].equalsIgnoreCase("leave")) {
            return handleLeave(sender);
        }

        if (!sender.hasPermission("fakerevive.admin")) {
            sender.sendMessage(Component.text(messageService.get("commands.fr.no-permission"), NamedTextColor.RED));
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
            case "team":
                return handleTeam(sender);
            case "armor":
                return handleArmor(sender, rest);
            case "reload":
                return handleReload(sender);
            case "help":
                return handleHelp(sender);
            default:
                sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.fr.usage"), NamedTextColor.RED)));
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("fakerevive.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return filterByPrefix(
                    List.of("revive", "undisguise", "disguise", "kit", "team", "armor", "leave", "reload", "help"),
                    args[0]);
        }

        return switch (args[0].toLowerCase()) {
            case "revive" -> completeRevive(args);
            case "undisguise" -> completeUndisguise(args);
            case "disguise" -> completeDisguise(args);
            case "kit" -> completeKit(args);
            case "armor" -> completeArmor(args);
            default -> List.of();
        };
    }

    private List<String> completeRevive(String[] args) {
        if (args.length == 2) {
            List<String> options = new ArrayList<>();
            options.add("@a");
            options.add("@p");
            options.add("@r");
            options.addAll(teamManager.getTeamNames());
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
            List<String> options = new ArrayList<>();
            options.add("@a");
            Bukkit.getOnlinePlayers().forEach(p -> {
                options.add(p.getName());
                activeDisguiseRegistry.getFakeName(p.getUniqueId()).ifPresent(options::add);
            });
            return filterByPrefix(options, args[1]);
        }
        return List.of();
    }

    private List<String> completeDisguise(String[] args) {
        if (args.length == 2) {
            List<String> options = new ArrayList<>();
            options.add("@p");
            options.add("@r");
            Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
            return filterByPrefix(options, args[1]);
        }
        return List.of();
    }

    private List<String> completeKit(String[] args) {
        if (args.length == 2) {
            return filterByPrefix(List.of("save", "list", "give", "equip", "delete"), args[1]);
        }
        if (args.length >= 3) {
            return switch (args[1].toLowerCase()) {
                case "give" -> {
                    if (args.length == 3) yield filterByPrefix(new ArrayList<>(kitManager.getKitNames()), args[2]);
                    if (args.length == 4) {
                        List<String> options = new ArrayList<>();
                        options.add("@a");
                        options.addAll(teamManager.getTeamNames());
                        Bukkit.getOnlinePlayers().forEach(p -> {
                            options.add(p.getName());
                            activeDisguiseRegistry.getFakeName(p.getUniqueId()).ifPresent(options::add);
                        });
                        yield filterByPrefix(options, args[3]);
                    }
                    yield List.of();
                }
                case "equip" -> {
                    if (args.length == 3) {
                        List<String> options = new ArrayList<>(kitManager.getKitNames());
                        options.addAll(teamManager.getTeamNames());
                        yield filterByPrefix(options, args[2]);
                    }
                    if (args.length == 4) {
                        List<String> options = new ArrayList<>();
                        options.add("@a");
                        options.addAll(teamManager.getTeamNames());
                        Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
                        yield filterByPrefix(options, args[3]);
                    }
                    yield List.of();
                }
                case "delete" -> {
                    if (args.length == 3) yield filterByPrefix(new ArrayList<>(kitManager.getKitNames()), args[2]);
                    yield List.of();
                }
                default -> List.of();
            };
        }
        return List.of();
    }

    private List<String> completeArmor(String[] args) {
        if (args.length == 2) {
            return filterByPrefix(List.of("on", "off", "status"), args[1]);
        }
        if (args.length == 3) {
            List<String> options = new ArrayList<>();
            // "status" only resolves a single player, so don't suggest group targets there.
            if (!args[1].equalsIgnoreCase("status")) {
                options.add("@a");
                options.addAll(teamManager.getTeamNames());
            }
            Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
            return filterByPrefix(options, args[2]);
        }
        return List.of();
    }

    private List<String> filterByPrefix(List<String> options, String prefix) {
        String lowerPrefix = prefix.toLowerCase();
        return options.stream()
                .filter(option -> option.toLowerCase().startsWith(lowerPrefix))
                .collect(Collectors.toList());
    }

    private boolean handleRevive(CommandSender sender, String[] args) {
        if (args.length < 1 || args.length > 3) {
            sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.revive.usage"), NamedTextColor.RED)));
            return true;
        }

        String kitName = null;
        String customName = null;
        if (args.length == 2) {
            if (kitManager.getKitNames().contains(args[1])) {
                kitName = args[1];
            } else {
                customName = args[1];
            }
        } else if (args.length == 3) {
            customName = args[1];
            kitName = args[2];
        }

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
                    revivePlayer(target, spawnLocation, kitName, null);
                    revivedCount++;
                }
            }

            if (revivedCount == 0) {
                sender.sendMessage(PREFIX.append(Component.text(
                        messageService.get("commands.revive.no-targets"),
                        NamedTextColor.YELLOW)));
            } else {
                sender.sendMessage(PREFIX.append(Component.text(
                        messageService.get("commands.revive.success-multiple", "count", String.valueOf(revivedCount)),
                        NamedTextColor.GREEN)));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("@p")) {
            if (!(sender instanceof Player senderPlayer)) {
                sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.revive.players-only"), NamedTextColor.RED)));
                return true;
            }

            int revivedCount = 0;
            for (Player target : senderPlayer.getWorld().getPlayers()) {
                if (target.getLocation().distanceSquared(senderPlayer.getLocation()) <= NEARBY_RADIUS_SQUARED
                        && fakeLeaveListener.isFakedOut(target.getUniqueId())) {
                    revivePlayer(target, spawnLocation, kitName, null);
                    revivedCount++;
                }
            }

            if (revivedCount == 0) {
                sender.sendMessage(PREFIX.append(Component.text(
                        messageService.get("commands.revive.no-targets"),
                        NamedTextColor.YELLOW)));
            } else {
                sender.sendMessage(PREFIX.append(Component.text(
                        messageService.get("commands.revive.success-multiple", "count", String.valueOf(revivedCount)),
                        NamedTextColor.GREEN)));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("@r")) {
            List<Player> fakedOutPlayers = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> fakeLeaveListener.isFakedOut(p.getUniqueId()))
                    .collect(Collectors.toList());

            if (fakedOutPlayers.isEmpty()) {
                sender.sendMessage(PREFIX.append(Component.text(
                        messageService.get("commands.revive.no-targets"), NamedTextColor.YELLOW)));
                return true;
            }

            Player target = fakedOutPlayers.get(random.nextInt(fakedOutPlayers.size()));
            revivePlayer(target, spawnLocation, kitName, null);
            sender.sendMessage(PREFIX.append(Component.text(
                    messageService.get("commands.revive.success-single", "player", target.getName()),
                    NamedTextColor.GREEN)));
            return true;
        }

        if (teamManager.teamExists(args[0])) {
            String teamKitName = kitName != null ? kitName : teamManager.getTeamKit(args[0]).orElse(null);
            int revivedCount = 0;

            for (UUID memberId : teamManager.getTeamMembers(args[0])) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null && fakeLeaveListener.isFakedOut(memberId)) {
                    revivePlayer(member, spawnLocation, teamKitName, null);
                    revivedCount++;
                }
            }

            if (revivedCount == 0) {
                sender.sendMessage(PREFIX.append(Component.text(
                        messageService.get("commands.revive.no-team-targets", "team", args[0]),
                        NamedTextColor.YELLOW)));
            } else {
                sender.sendMessage(PREFIX.append(Component.text(
                        messageService.get("commands.revive.success-team",
                                "count", String.valueOf(revivedCount), "team", args[0]),
                        NamedTextColor.GREEN)));
            }
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);

        if (target == null) {
            sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.revive.not-found"), NamedTextColor.RED)));
            return true;
        }

        if (!fakeLeaveListener.isFakedOut(target.getUniqueId())) {
            sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.revive.not-in-spectator"), NamedTextColor.RED)));
            return true;
        }

        if (customName != null) {
            revivePlayerWithCustomName(target, spawnLocation, customName, kitName, sender);
            sender.sendMessage(PREFIX.append(Component.text(
                    messageService.get("commands.revive.fetching-profile", "name", customName),
                    NamedTextColor.GRAY)));
        } else {
            revivePlayer(target, spawnLocation, kitName, null);
            sender.sendMessage(PREFIX.append(Component.text(
                    messageService.get("commands.revive.success-single", "player", target.getName()),
                    NamedTextColor.GREEN)));
        }
        return true;
    }

    private boolean handleUndisguise(CommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("@a")) {
            int count = 0;
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (activeDisguiseRegistry.getIdentity(online.getUniqueId()).isPresent()) {
                    activeDisguiseRegistry.clear(online.getUniqueId());
                    playerDisguiseService.remove(online);
                    online.sendMessage(PREFIX.append(Component.text(
                            messageService.get("commands.undisguise.success-self"), NamedTextColor.YELLOW)));
                    count++;
                }
            }
            sender.sendMessage(PREFIX.append(Component.text(
                    messageService.get("commands.undisguise.success-all", "count", String.valueOf(count)),
                    NamedTextColor.GREEN)));
            return true;
        }

        Player target;
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.undisguise.usage"), NamedTextColor.RED)));
                return true;
            }
            target = (Player) sender;
        } else {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                UUID byFakeName = activeDisguiseRegistry.findByFakeName(args[0]).orElse(null);
                if (byFakeName != null) {
                    target = Bukkit.getPlayer(byFakeName);
                }
            }
            if (target == null) {
                sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.undisguise.not-found"), NamedTextColor.RED)));
                return true;
            }
        }

        if (activeDisguiseRegistry.getIdentity(target.getUniqueId()).isEmpty()) {
            sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.undisguise.not-disguised"), NamedTextColor.RED)));
            return true;
        }

        activeDisguiseRegistry.clear(target.getUniqueId());
        playerDisguiseService.remove(target);
        target.sendMessage(PREFIX.append(Component.text(messageService.get("commands.undisguise.success-self"), NamedTextColor.YELLOW)));

        if (sender != target) {
            sender.sendMessage(PREFIX.append(Component.text(
                    messageService.get("commands.undisguise.success-other", "player", target.getName()),
                    NamedTextColor.GREEN)));
        }

        return true;
    }

    private boolean handleDisguise(CommandSender sender, String[] args) {
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.disguise.usage"), NamedTextColor.RED)));
            return true;
        }

        String customName = args.length == 2 ? args[1] : null;

        if (args[0].equalsIgnoreCase("@p")) {
            if (!(sender instanceof Player senderPlayer)) {
                sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.revive.players-only"), NamedTextColor.RED)));
                return true;
            }

            int count = 0;
            for (Player nearby : senderPlayer.getWorld().getPlayers()) {
                if (nearby.getLocation().distanceSquared(senderPlayer.getLocation()) <= NEARBY_RADIUS_SQUARED) {
                    disguiseWithRandomIdentity(nearby, null, sender);
                    count++;
                }
            }
            sender.sendMessage(PREFIX.append(Component.text(
                    messageService.get("commands.disguise.success-multiple", "count", String.valueOf(count)),
                    NamedTextColor.GREEN)));
            return true;
        }

        if (args[0].equalsIgnoreCase("@r")) {
            List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (online.isEmpty()) {
                sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.disguise.not-found"), NamedTextColor.RED)));
                return true;
            }

            Player target = online.get(random.nextInt(online.size()));
            disguiseWithRandomIdentity(target, null, sender);
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.disguise.not-found"), NamedTextColor.RED)));
            return true;
        }

        if (customName != null) {
            String finalCustomName = customName;
            sender.sendMessage(PREFIX.append(Component.text(
                    messageService.get("commands.revive.fetching-profile", "name", customName),
                    NamedTextColor.GRAY)));
            activeDisguiseRegistry.clear(target.getUniqueId());

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                Optional<FakeIdentity> identity = identityFetcher.fetchIdentityByName(finalCustomName);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (identity.isEmpty()) {
                        sender.sendMessage(PREFIX.append(Component.text(
                                messageService.get("commands.disguise.name-not-found", "name", finalCustomName),
                                NamedTextColor.RED)));
                        return;
                    }
                    applyDisguiseIdentity(target, identity.get(), null, sender);
                });
            });
            return true;
        }

        disguiseWithRandomIdentity(target, null, sender);
        return true;
    }

    private boolean handleKit(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.kit.usage"), NamedTextColor.RED)));
            return true;
        }

        if (args[0].equalsIgnoreCase("save")) {
            if (args.length != 2) {
                sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.kit.usage"), NamedTextColor.RED)));
                return true;
            }

            if (!(sender instanceof Player)) {
                sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.kit.players-only"), NamedTextColor.RED)));
                return true;
            }

            String name = args[1];
            kitManager.saveKit(name, (Player) sender);
            sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.kit.saved", "name", name), NamedTextColor.GREEN)));
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            if (args.length != 1) {
                sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.kit.usage"), NamedTextColor.RED)));
                return true;
            }

            Set<String> kitNames = kitManager.getKitNames();
            if (kitNames.isEmpty()) {
                sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.kit.list-empty"), NamedTextColor.YELLOW)));
            } else {
                sender.sendMessage(PREFIX.append(Component.text(
                        messageService.get("commands.kit.list-format", "kits", String.join(", ", kitNames)),
                        NamedTextColor.GREEN)));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            return handleKitGive(sender, args);
        }

        if (args[0].equalsIgnoreCase("equip")) {
            return handleKitEquip(sender, args);
        }

        if (args[0].equalsIgnoreCase("delete")) {
            if (args.length != 2) {
                sender.sendMessage(PREFIX.append(Component.text(
                        messageService.get("commands.kit.delete-usage"), NamedTextColor.RED)));
                return true;
            }

            if (!kitManager.deleteKit(args[1])) {
                sender.sendMessage(PREFIX.append(Component.text(
                        messageService.get("commands.kit.delete-not-found", "name", args[1]),
                        NamedTextColor.RED)));
                return true;
            }

            sender.sendMessage(PREFIX.append(Component.text(
                    messageService.get("commands.kit.deleted", "name", args[1]), NamedTextColor.GREEN)));
            return true;
        }

        sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.kit.usage"), NamedTextColor.RED)));
        return true;
    }

    private boolean handleKitGive(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.kit.give-usage"), NamedTextColor.RED)));
            return true;
        }

        String name = args[1];
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            UUID byFakeName = activeDisguiseRegistry.findByFakeName(args[2]).orElse(null);
            if (byFakeName != null) {
                target = Bukkit.getPlayer(byFakeName);
            }
        }
        if (args[2].equalsIgnoreCase("@a")) {
            if (!kitManager.getKitNames().contains(name)) {
                sender.sendMessage(PREFIX.append(Component.text(
                        messageService.get("commands.kit.give-kit-not-found", "name", name), NamedTextColor.RED)));
                return true;
            }

            int count = 0;
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (plugin.getConfig().getBoolean("kits.clear-before-give", false)) {
                    online.getInventory().clear();
                }
                kitManager.giveKit(name, online);
                count++;
            }

            sender.sendMessage(PREFIX.append(Component.text(
                    messageService.get("commands.kit.give-all-success", "name", name, "count", String.valueOf(count)),
                    NamedTextColor.GREEN)));
            return true;
        }

        if (teamManager.teamExists(args[2])) {
            if (!kitManager.getKitNames().contains(name)) {
                sender.sendMessage(PREFIX.append(Component.text(
                        messageService.get("commands.kit.give-kit-not-found", "name", name), NamedTextColor.RED)));
                return true;
            }

            int count = 0;
            for (UUID memberId : teamManager.getTeamMembers(args[2])) {
                Player member = Bukkit.getPlayer(memberId);
                if (member == null) continue;
                if (plugin.getConfig().getBoolean("kits.clear-before-give", false)) {
                    member.getInventory().clear();
                }
                kitManager.giveKit(name, member);
                count++;
            }

            sender.sendMessage(PREFIX.append(Component.text(
                    messageService.get("commands.kit.give-team-success",
                            "name", name, "count", String.valueOf(count), "team", args[2]),
                    NamedTextColor.GREEN)));
            return true;
        }

        if (target == null) {
            sender.sendMessage(PREFIX.append(Component.text(
                    messageService.get("commands.kit.give-player-not-found", "player", args[2]), NamedTextColor.RED)));
            return true;
        }

        if (plugin.getConfig().getBoolean("kits.clear-before-give", false)) {
            target.getInventory().clear();
        }

        if (!kitManager.giveKit(name, target)) {
            sender.sendMessage(PREFIX.append(Component.text(
                    messageService.get("commands.kit.give-kit-not-found", "name", name), NamedTextColor.RED)));
            return true;
        }

        sender.sendMessage(PREFIX.append(Component.text(
                messageService.get("commands.kit.give-success", "name", name, "player", target.getName()),
                NamedTextColor.GREEN)));
        return true;
    }

    private boolean handleKitEquip(CommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.kit.equip-usage"), NamedTextColor.RED)));
            return true;
        }

        String name = args[1];
        Player target;
        boolean equippingOther;
        if (args.length == 3) {
            if (args[2].equalsIgnoreCase("@a")) {
                int equipped = 0;
                for (Player online : Bukkit.getOnlinePlayers()) {
                    clearIfConfigured(online);
                    if (kitManager.equipKit(name, online)) {
                        equipped++;
                    }
                }
                sender.sendMessage(PREFIX.append(Component.text(
                        messageService.get("commands.kit.equip-all-success",
                                "name", name, "count", String.valueOf(equipped)),
                        NamedTextColor.GREEN)));
                return true;
            }

            if (teamManager.teamExists(args[2])) {
                return equipTeam(sender, name, args[2]);
            }

            target = Bukkit.getPlayer(args[2]);
            equippingOther = true;
            if (target == null) {
                sender.sendMessage(PREFIX.append(Component.text(
                        messageService.get("commands.kit.equip-player-not-found", "player", args[2]), NamedTextColor.RED)));
                return true;
            }
        } else {
            // A team name takes precedence over an equally named kit, so "/fr kit equip <team>"
            // equips the team's own assigned kit to all its members instead of the sender.
            if (teamManager.teamExists(name)) {
                return equipTeamKit(sender, name);
            }

            if (!(sender instanceof Player)) {
                sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.kit.players-only"), NamedTextColor.RED)));
                return true;
            }
            target = (Player) sender;
            equippingOther = false;
        }

        clearIfConfigured(target);

        if (!kitManager.equipKit(name, target)) {
            sender.sendMessage(PREFIX.append(Component.text(
                    messageService.get("commands.kit.equip-kit-not-found", "name", name), NamedTextColor.RED)));
            return true;
        }

        if (equippingOther) {
            sender.sendMessage(PREFIX.append(Component.text(
                    messageService.get("commands.kit.equip-success-other", "name", name, "player", target.getName()),
                    NamedTextColor.GREEN)));
        } else {
            sender.sendMessage(PREFIX.append(Component.text(
                    messageService.get("commands.kit.equip-success", "name", name), NamedTextColor.GREEN)));
        }
        return true;
    }

    /**
     * Equips the kit assigned to a team in {@code teams.yml} onto all of its online members.
     * Reports an error if the team has no kit, or if its kit reference is stale - deleting a kit
     * via {@code /fr kit delete} leaves the team's reference dangling.
     */
    private boolean equipTeamKit(CommandSender sender, String teamName) {
        String teamKit = teamManager.getTeamKit(teamName).orElse(null);
        if (teamKit == null) {
            sender.sendMessage(PREFIX.append(Component.text(
                    messageService.get("commands.kit.equip-team-no-kit", "team", teamName), NamedTextColor.RED)));
            return true;
        }

        if (!kitManager.getKitNames().contains(teamKit)) {
            sender.sendMessage(PREFIX.append(Component.text(
                    messageService.get("commands.kit.equip-kit-not-found", "name", teamKit), NamedTextColor.RED)));
            return true;
        }

        return equipTeam(sender, teamKit, teamName);
    }

    /** Equips the named kit onto every online member of the team. */
    private boolean equipTeam(CommandSender sender, String kitName, String teamName) {
        int equipped = 0;
        for (UUID memberId : teamManager.getTeamMembers(teamName)) {
            Player member = Bukkit.getPlayer(memberId);
            if (member == null) {
                continue;
            }
            clearIfConfigured(member);
            if (kitManager.equipKit(kitName, member)) {
                equipped++;
            }
        }

        sender.sendMessage(PREFIX.append(Component.text(
                messageService.get("commands.kit.equip-team-success",
                        "name", kitName, "count", String.valueOf(equipped), "team", teamName),
                NamedTextColor.GREEN)));
        return true;
    }

    /** Wipes inventory, armor, and offhand, but only when {@code kits.clear-before-equip} is enabled. */
    private void clearIfConfigured(Player player) {
        if (!plugin.getConfig().getBoolean("kits.clear-before-equip", false)) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[ARMOR_SLOTS]);
        inventory.setItemInOffHand(null);
    }

    private boolean handleTeam(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.team.players-only"), NamedTextColor.RED)));
            return true;
        }

        teamGui.openMainMenu(player);
        return true;
    }

    /**
     * Locks or unlocks armor for a target, mimicking Curse of Binding without enchanting the items.
     * Locks are stored by UUID and persist across logouts, so a team target locks every member -
     * including offline ones - while {@code @a} only covers players who are currently online.
     */
    private boolean handleArmor(CommandSender sender, String[] args) {
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.armor.usage"), NamedTextColor.RED)));
            return true;
        }

        String action = args[0].toLowerCase();
        if (!action.equals("on") && !action.equals("off") && !action.equals("status")) {
            sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.armor.usage"), NamedTextColor.RED)));
            return true;
        }

        if (action.equals("status")) {
            return handleArmorStatus(sender, args.length == 2 ? args[1] : null);
        }

        boolean lock = action.equals("on");

        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(PREFIX.append(Component.text(
                        messageService.get("commands.armor.players-only"), NamedTextColor.RED)));
                return true;
            }

            setArmorLock(player.getUniqueId(), lock);
            sender.sendMessage(PREFIX.append(Component.text(
                    messageService.get(lock ? "commands.armor.on-self" : "commands.armor.off-self"),
                    NamedTextColor.GREEN)));
            return true;
        }

        String targetArg = args[1];

        if (targetArg.equalsIgnoreCase("@a")) {
            int count = 0;
            for (Player online : Bukkit.getOnlinePlayers()) {
                setArmorLock(online.getUniqueId(), lock);
                count++;
            }
            sender.sendMessage(PREFIX.append(Component.text(
                    messageService.get(lock ? "commands.armor.on-all" : "commands.armor.off-all",
                            "count", String.valueOf(count)),
                    NamedTextColor.GREEN)));
            return true;
        }

        if (teamManager.teamExists(targetArg)) {
            int count = 0;
            for (UUID memberId : teamManager.getTeamMembers(targetArg)) {
                setArmorLock(memberId, lock);
                count++;
            }
            sender.sendMessage(PREFIX.append(Component.text(
                    messageService.get(lock ? "commands.armor.on-team" : "commands.armor.off-team",
                            "count", String.valueOf(count), "team", targetArg),
                    NamedTextColor.GREEN)));
            return true;
        }

        Player target = Bukkit.getPlayer(targetArg);
        if (target == null) {
            sender.sendMessage(PREFIX.append(Component.text(
                    messageService.get("commands.armor.player-not-found", "player", targetArg), NamedTextColor.RED)));
            return true;
        }

        setArmorLock(target.getUniqueId(), lock);
        sender.sendMessage(PREFIX.append(Component.text(
                messageService.get(lock ? "commands.armor.on-other" : "commands.armor.off-other",
                        "player", target.getName()),
                NamedTextColor.GREEN)));
        return true;
    }

    private boolean handleArmorStatus(CommandSender sender, @Nullable String targetArg) {
        if (targetArg != null) {
            Player target = Bukkit.getPlayer(targetArg);
            if (target == null) {
                sender.sendMessage(PREFIX.append(Component.text(
                        messageService.get("commands.armor.player-not-found", "player", targetArg), NamedTextColor.RED)));
                return true;
            }

            boolean locked = armorLockManager.isLocked(target.getUniqueId());
            sender.sendMessage(PREFIX.append(Component.text(
                    messageService.get(locked ? "commands.armor.status-locked" : "commands.armor.status-unlocked",
                            "player", target.getName()),
                    locked ? NamedTextColor.YELLOW : NamedTextColor.GREEN)));
            return true;
        }

        List<String> names = new ArrayList<>();
        for (UUID lockedId : armorLockManager.getLocked()) {
            Player online = Bukkit.getPlayer(lockedId);
            names.add(online != null ? online.getName() : lockedId.toString());
        }

        if (names.isEmpty()) {
            sender.sendMessage(PREFIX.append(Component.text(
                    messageService.get("commands.armor.status-none"), NamedTextColor.YELLOW)));
            return true;
        }

        sender.sendMessage(PREFIX.append(Component.text(
                messageService.get("commands.armor.status-list", "players", String.join(", ", names)),
                NamedTextColor.GREEN)));
        return true;
    }

    private void setArmorLock(UUID playerId, boolean lock) {
        if (lock) {
            armorLockManager.lock(playerId);
        } else {
            armorLockManager.unlock(playerId);
        }
    }

    private boolean handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.leave.players-only"), NamedTextColor.RED)));
            return true;
        }

        if (!player.hasPermission("fakerevive.leave")) {
            player.sendMessage(PREFIX.append(Component.text(messageService.get("commands.leave.no-permission"), NamedTextColor.RED)));
            return true;
        }

        Optional<String> team = teamManager.getPlayerTeam(player.getUniqueId());
        if (team.isEmpty()) {
            player.sendMessage(PREFIX.append(Component.text(messageService.get("commands.leave.no-team"), NamedTextColor.RED)));
            return true;
        }

        teamManager.removePlayer(player.getUniqueId());
        player.sendMessage(PREFIX.append(Component.text(
                messageService.get("commands.leave.success", "team", team.get()),
                NamedTextColor.YELLOW)));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        plugin.reloadConfig();
        messageService.reload(plugin);
        kitManager.loadKits();
        teamManager.reload();
        armorLockManager.loadLocks();
        sender.sendMessage(PREFIX.append(Component.text(
                messageService.get("commands.reload.success"), NamedTextColor.GREEN)));
        return true;
    }

    private boolean handleHelp(CommandSender sender) {
        sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.help.header"), NamedTextColor.GOLD)));
        sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.help.revive"), NamedTextColor.YELLOW)));
        sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.help.undisguise"), NamedTextColor.YELLOW)));
        sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.help.disguise"), NamedTextColor.YELLOW)));
        sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.help.kit-save"), NamedTextColor.YELLOW)));
        sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.help.kit-list"), NamedTextColor.YELLOW)));
        sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.help.kit-delete"), NamedTextColor.YELLOW)));
        sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.help.kit-give"), NamedTextColor.YELLOW)));
        sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.help.kit-equip"), NamedTextColor.YELLOW)));
        sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.help.team"), NamedTextColor.YELLOW)));
        sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.help.armor"), NamedTextColor.YELLOW)));
        sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.help.leave"), NamedTextColor.YELLOW)));
        sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.help.reload"), NamedTextColor.YELLOW)));
        sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.help.help"), NamedTextColor.YELLOW)));
        return true;
    }

    private void revivePlayer(Player player, Location location, @Nullable String kitName, @Nullable FakeIdentity forcedIdentity) {
        fakeLeaveListener.clearFakedOut(player.getUniqueId());
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(location);

        Optional<FakeIdentity> identity = forcedIdentity != null
                ? Optional.of(forcedIdentity)
                : fakeNamePool.assignRandomIdentity();

        if (identity.isPresent()) {
            FakeIdentity fakeIdentity = identity.get();
            activeDisguiseRegistry.assign(player.getUniqueId(), fakeIdentity);
            playerDisguiseService.apply(player, fakeIdentity);

            String fakeName = fakeIdentity.name();
            player.sendMessage(PREFIX.append(Component.text(
                    messageService.get("disguise.applied", "name", fakeName), NamedTextColor.YELLOW)));
            player.sendActionBar(PREFIX.append(Component.text(
                    messageService.get("disguise.applied-actionbar", "name", fakeName), NamedTextColor.YELLOW)));

            if (forcedIdentity == null) {
                replenishPool();
            }
        } else {
            logger.warning(messageService.get("events.pool-exhausted", "player", player.getName()));
        }

        if (kitName != null) {
            applyKit(player, kitName);
        }
    }

    /**
     * Revives the player immediately, then applies a real Minecraft account's name and skin once
     * fetched from Mojang, instead of a randomly generated identity. The fetch runs off the main
     * thread so the player isn't left waiting in spectator mode for the network round trip;
     * {@code resultTarget} is notified once it completes, whether it succeeded or not.
     */
    private void revivePlayerWithCustomName(Player player, Location location, String minecraftName,
                                             @Nullable String kitName, CommandSender resultTarget) {
        fakeLeaveListener.clearFakedOut(player.getUniqueId());
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(location);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Optional<FakeIdentity> identity = identityFetcher.fetchIdentityByName(minecraftName);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (identity.isEmpty()) {
                    resultTarget.sendMessage(PREFIX.append(Component.text(
                            messageService.get("commands.disguise.name-not-found", "name", minecraftName),
                            NamedTextColor.RED)));
                    return;
                }
                revivePlayer(player, location, kitName, identity.get());
                resultTarget.sendMessage(PREFIX.append(Component.text(
                        messageService.get("commands.revive.success-single", "player", player.getName()),
                        NamedTextColor.GREEN)));
            });
        });
    }

    private void disguiseWithRandomIdentity(Player target, @Nullable String kitName, CommandSender resultSender) {
        activeDisguiseRegistry.clear(target.getUniqueId());
        Optional<FakeIdentity> identity = fakeNamePool.assignRandomIdentity();
        if (identity.isEmpty()) {
            resultSender.sendMessage(PREFIX.append(Component.text(
                    messageService.get("commands.disguise.pool-exhausted"), NamedTextColor.RED)));
            logger.warning(messageService.get("events.pool-exhausted", "player", target.getName()));
            return;
        }

        applyDisguiseIdentity(target, identity.get(), kitName, resultSender);
        replenishPool();
    }

    private void applyDisguiseIdentity(Player target, FakeIdentity fakeIdentity, @Nullable String kitName, CommandSender resultSender) {
        activeDisguiseRegistry.assign(target.getUniqueId(), fakeIdentity);
        playerDisguiseService.apply(target, fakeIdentity);

        String fakeName = fakeIdentity.name();
        target.sendMessage(PREFIX.append(Component.text(
                messageService.get("disguise.applied", "name", fakeName), NamedTextColor.YELLOW)));
        target.sendActionBar(PREFIX.append(Component.text(
                messageService.get("disguise.applied-actionbar", "name", fakeName), NamedTextColor.YELLOW)));

        if (!resultSender.equals(target)) {
            resultSender.sendMessage(PREFIX.append(Component.text(
                    messageService.get("commands.disguise.success", "player", target.getName(), "name", fakeName),
                    NamedTextColor.GREEN)));
        }

        if (kitName != null) {
            applyKit(target, kitName);
        }
    }

    private void applyKit(Player player, String kitName) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(null);
        inventory.setItemInOffHand(null);
        player.updateInventory();
        if (!kitManager.equipKit(kitName, player)) {
            logger.warning("Kit \"" + kitName + "\" not found for player " + player.getName());
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
