package com.fakerevive.command;

import com.fakerevive.disguise.DisguiseCoordinator;
import com.fakerevive.kit.KitManager;
import com.fakerevive.message.MessageService;
import com.fakerevive.storage.FakedOutPlayerStore;
import com.fakerevive.team.TeamManager;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.IntFunction;

public class ReviveCommandHandler implements SubcommandHandler {
    private static final String[] NO_PLACEHOLDERS = new String[0];

    private final MessageService messageService;
    private final FakedOutPlayerStore fakedOutPlayerStore;
    private final TeamManager teamManager;
    private final KitManager kitManager;
    private final DisguiseCoordinator disguiseCoordinator;
    private final Random random = new Random();

    public ReviveCommandHandler(MessageService messageService, FakedOutPlayerStore fakedOutPlayerStore,
                                TeamManager teamManager, KitManager kitManager,
                                DisguiseCoordinator disguiseCoordinator) {
        this.messageService = messageService;
        this.fakedOutPlayerStore = fakedOutPlayerStore;
        this.teamManager = teamManager;
        this.kitManager = kitManager;
        this.disguiseCoordinator = disguiseCoordinator;
    }

    @Override
    public String name() {
        return "revive";
    }

    @Override
    public String permission() {
        return CommandPermissions.REVIVE;
    }

    @Override
    public boolean execute(CommandSender sender, String[] arguments) {
        if (arguments.length < 1 || arguments.length > 3) {
            messageService.send(sender, "commands.revive.usage", NamedTextColor.RED);
            return true;
        }

        String kitName = null;
        String customName = null;
        if (arguments.length == 2) {
            if (kitManager.getKitNames().contains(arguments[1])) {
                kitName = arguments[1];
            } else {
                customName = arguments[1];
            }
        } else if (arguments.length == 3) {
            customName = arguments[1];
            kitName = arguments[2];
        }

        Location spawnLocation = sender instanceof Player senderPlayer
                ? senderPlayer.getLocation()
                : Bukkit.getWorlds().get(0).getSpawnLocation();
        String targetArgument = arguments[0];

        if (targetArgument.equalsIgnoreCase("@a")) {
            reviveMultiple(sender, fakedOutOnlinePlayers(), spawnLocation, kitName);
            return true;
        }

        if (NearbyPlayerResolver.isNearestSelector(targetArgument)) {
            if (!(sender instanceof Player senderPlayer)) {
                messageService.send(sender, "commands.revive.players-only", NamedTextColor.RED);
                return true;
            }

            Optional<Player> nearest = NearbyPlayerResolver.resolveNearest(senderPlayer, this::isFakedOut);
            if (nearest.isEmpty()) {
                messageService.send(sender, "commands.revive.no-targets", NamedTextColor.YELLOW);
                return true;
            }

            reviveSingle(sender, nearest.get(), spawnLocation, kitName);
            return true;
        }

        if (NearbyPlayerResolver.isNearSelector(targetArgument)) {
            if (!(sender instanceof Player senderPlayer)) {
                messageService.send(sender, "commands.revive.players-only", NamedTextColor.RED);
                return true;
            }

            reviveMultiple(sender, NearbyPlayerResolver.resolveNear(senderPlayer, this::isFakedOut), spawnLocation,
                    kitName);
            return true;
        }

        if (targetArgument.equalsIgnoreCase("@r")) {
            List<Player> fakedOutPlayers = fakedOutOnlinePlayers();
            if (fakedOutPlayers.isEmpty()) {
                messageService.send(sender, "commands.revive.no-targets", NamedTextColor.YELLOW);
                return true;
            }

            reviveSingle(sender, fakedOutPlayers.get(random.nextInt(fakedOutPlayers.size())), spawnLocation,
                    kitName);
            return true;
        }

        if (teamManager.teamExists(targetArgument)) {
            String teamKitName = kitName != null ? kitName : teamManager.getTeamKit(targetArgument).orElse(null);
            List<Player> fakedOutMembers = new ArrayList<>();
            for (UUID memberId : teamManager.getTeamMembers(targetArgument)) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null && fakedOutPlayerStore.contains(memberId)) {
                    fakedOutMembers.add(member);
                }
            }
            reviveGroup(sender, fakedOutMembers, spawnLocation, teamKitName,
                    "commands.revive.no-team-targets", new String[]{"team", targetArgument},
                    "commands.revive.success-team",
                    count -> new String[]{"count", String.valueOf(count), "team", targetArgument});
            return true;
        }

        Player target = Bukkit.getPlayer(targetArgument);

        if (target == null) {
            messageService.send(sender, "commands.revive.not-found", NamedTextColor.RED);
            return true;
        }

        if (!isFakedOut(target)) {
            messageService.send(sender, "commands.revive.not-in-spectator", NamedTextColor.RED);
            return true;
        }

        if (customName != null) {
            reviveWithCustomName(target, spawnLocation, customName, kitName, sender);
            messageService.send(sender, "commands.revive.fetching-profile", NamedTextColor.GRAY, "name", customName);
        } else {
            reviveSingle(sender, target, spawnLocation, kitName);
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] arguments) {
        if (arguments.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("@a");
            options.add(NearbyPlayerResolver.NEAREST_SELECTOR);
            options.add(NearbyPlayerResolver.NEAR_SELECTOR);
            options.add("@r");
            options.addAll(teamManager.getTeamNames());
            Bukkit.getOnlinePlayers().forEach(online -> options.add(online.getName()));
            return TabCompletions.filterByPrefix(options, arguments[0]);
        }
        if (arguments.length == 2) {
            return TabCompletions.filterByPrefix(new ArrayList<>(kitManager.getKitNames()), arguments[1]);
        }
        return List.of();
    }

    private boolean isFakedOut(Player player) {
        return fakedOutPlayerStore.contains(player.getUniqueId());
    }

    private List<Player> fakedOutOnlinePlayers() {
        List<Player> fakedOutPlayers = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (isFakedOut(online)) {
                fakedOutPlayers.add(online);
            }
        }
        return fakedOutPlayers;
    }

    private void reviveMultiple(CommandSender sender, List<Player> targets, Location location,
                                @Nullable String kitName) {
        reviveGroup(sender, targets, location, kitName, "commands.revive.no-targets", NO_PLACEHOLDERS,
                "commands.revive.success-multiple", count -> new String[]{"count", String.valueOf(count)});
    }

    private void reviveGroup(CommandSender sender, List<Player> targets, Location location,
                             @Nullable String kitName, String noTargetsKey, String[] noTargetsPlaceholders,
                             String successKey, IntFunction<String[]> successPlaceholders) {
        if (targets.isEmpty()) {
            messageService.send(sender, noTargetsKey, NamedTextColor.YELLOW, noTargetsPlaceholders);
            return;
        }

        int undisguisedCount = 0;
        for (Player target : targets) {
            if (!revivePlayer(target, location, kitName)) {
                undisguisedCount++;
            }
        }

        messageService.send(sender, successKey, NamedTextColor.GREEN, successPlaceholders.apply(targets.size()));
        if (undisguisedCount > 0) {
            messageService.send(sender, "commands.revive.pool-exhausted-multiple", NamedTextColor.RED, "count",
                    String.valueOf(undisguisedCount));
        }
    }

    private void reviveSingle(CommandSender sender, Player target, Location location, @Nullable String kitName) {
        boolean disguised = revivePlayer(target, location, kitName);
        messageService.send(sender, "commands.revive.success-single", NamedTextColor.GREEN, "player",
                target.getName());
        if (!disguised) {
            messageService.send(sender, "commands.revive.pool-exhausted", NamedTextColor.RED, "player",
                    target.getName());
        }
    }

    private boolean revivePlayer(Player player, Location location, @Nullable String kitName) {
        reviveInPlace(player, location);

        boolean disguised = disguiseCoordinator.assignFromPool(player).isPresent();

        if (kitName != null) {
            disguiseCoordinator.applyKit(player, kitName);
        }
        return disguised;
    }

    private void reviveWithCustomName(Player player, Location location, String minecraftName,
                                      @Nullable String kitName, CommandSender resultTarget) {
        reviveInPlace(player, location);

        if (kitName != null) {
            disguiseCoordinator.applyKit(player, kitName);
        }

        disguiseCoordinator.fetchIdentityAsynchronously(resultTarget, minecraftName, fetchedIdentity -> {
            if (!player.isOnline()) {
                messageService.send(resultTarget, "commands.revive.not-found", NamedTextColor.RED);
                return;
            }
            if (fetchedIdentity.isEmpty()) {
                messageService.send(resultTarget, "commands.disguise.name-not-found", NamedTextColor.RED, "name",
                        minecraftName);
                return;
            }
            disguiseCoordinator.applyIdentity(player, fetchedIdentity.get());
            messageService.send(resultTarget, "commands.revive.success-single", NamedTextColor.GREEN, "player",
                    player.getName());
        });
    }

    private void reviveInPlace(Player player, Location location) {
        fakedOutPlayerStore.remove(player.getUniqueId());
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(location);
    }
}
