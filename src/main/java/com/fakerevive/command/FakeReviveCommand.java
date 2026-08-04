package com.fakerevive.command;

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
 * Handles all {@code /fr} subcommands: revive, undisguise, disguise, kit (save/list/give/equip),
 * team, leave, reload, and help. Also provides context-aware tab completion for all subcommands.
 */
public class FakeReviveCommand implements CommandExecutor, TabCompleter {

    private static final int REPLENISH_MAX_ATTEMPTS = 25;
    private static final int NEARBY_RADIUS_SQUARED = 16 * 16;

    private final JavaPlugin plugin;
    private final FakeLeaveListener fakeLeaveListener;
    private final FakeNamePool fakeNamePool;
    private final ActiveDisguiseRegistry activeDisguiseRegistry;
    private final PlayerDisguiseService playerDisguiseService;
    private final MojangIdentityFetcher identityFetcher;
    private final KitManager kitManager;
    private final TeamManager teamManager;
    private final TeamGui teamGui;
    private final Logger logger;
    private final MessageService messageService;
    private final AtomicBoolean replenishInProgress = new AtomicBoolean(false);
    private final Random random = new Random();
    public static final Component PREFIX = Component.text("[FakeRevive] ", NamedTextColor.AQUA);

    public FakeReviveCommand(JavaPlugin plugin, FakeLeaveListener fakeLeaveListener, FakeNamePool fakeNamePool,
                              ActiveDisguiseRegistry activeDisguiseRegistry, PlayerDisguiseService playerDisguiseService,
                              MojangIdentityFetcher identityFetcher, KitManager kitManager, TeamManager teamManager,
                              TeamGui teamGui, Logger logger, MessageService messageService) {
        this.plugin = plugin;
        this.fakeLeaveListener = fakeLeaveListener;
        this.fakeNamePool = fakeNamePool;
        this.activeDisguiseRegistry = activeDisguiseRegistry;
        this.playerDisguiseService = playerDisguiseService;
        this.identityFetcher = identityFetcher;
        this.kitManager = kitManager;
        this.teamManager = teamManager;
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
            return filterByPrefix(List.of("revive", "undisguise", "disguise", "kit", "team", "leave", "reload", "help"), args[0]);
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
            return filterByPrefix(List.of("save", "list", "give", "equip"), args[1]);
        }
        if (args.length >= 3) {
            return switch (args[1].toLowerCase()) {
                case "give" -> {
                    if (args.length == 3) yield filterByPrefix(new ArrayList<>(kitManager.getKitNames()), args[2]);
                    if (args.length == 4) {
                        List<String> options = new ArrayList<>();
                        Bukkit.getOnlinePlayers().forEach(p -> {
                            options.add(p.getName());
                            activeDisguiseRegistry.getFakeName(p.getUniqueId()).ifPresent(options::add);
                        });
                        yield filterByPrefix(options, args[3]);
                    }
                    yield List.of();
                }
                case "equip" -> {
                    if (args.length == 3) yield filterByPrefix(new ArrayList<>(kitManager.getKitNames()), args[2]);
                    if (args.length == 4) {
                        List<String> options = new ArrayList<>(teamManager.getTeamNames());
                        Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
                        yield filterByPrefix(options, args[3]);
                    }
                    yield List.of();
                }
                default -> List.of();
            };
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
            if (teamManager.teamExists(args[2])) {
                int equipped = 0;
                for (UUID memberId : teamManager.getTeamMembers(args[2])) {
                    Player member = Bukkit.getPlayer(memberId);
                    if (member == null) continue;
                    if (plugin.getConfig().getBoolean("kits.clear-before-equip", false)) {
                        PlayerInventory memberInventory = member.getInventory();
                        memberInventory.clear();
                        memberInventory.setArmorContents(null);
                        memberInventory.setItemInOffHand(null);
                    }
                    if (kitManager.equipKit(name, member)) {
                        equipped++;
                    }
                }
                sender.sendMessage(PREFIX.append(Component.text(
                        messageService.get("commands.kit.equip-team-success",
                                "name", name, "count", String.valueOf(equipped), "team", args[2]),
                        NamedTextColor.GREEN)));
                return true;
            }

            target = Bukkit.getPlayer(args[2]);
            equippingOther = true;
            if (target == null) {
                sender.sendMessage(PREFIX.append(Component.text(
                        messageService.get("commands.kit.equip-player-not-found", "player", args[2]), NamedTextColor.RED)));
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.kit.players-only"), NamedTextColor.RED)));
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

    private boolean handleTeam(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.team.players-only"), NamedTextColor.RED)));
            return true;
        }

        teamGui.openMainMenu(player);
        return true;
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
        sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.help.kit-give"), NamedTextColor.YELLOW)));
        sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.help.kit-equip"), NamedTextColor.YELLOW)));
        sender.sendMessage(PREFIX.append(Component.text(messageService.get("commands.help.team"), NamedTextColor.YELLOW)));
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
