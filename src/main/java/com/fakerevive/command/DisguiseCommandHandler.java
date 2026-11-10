package com.fakerevive.command;

import com.fakerevive.disguise.DisguiseCoordinator;
import com.fakerevive.disguise.FakeIdentity;
import com.fakerevive.exempt.ExemptManager;
import com.fakerevive.message.MessageService;
import com.fakerevive.storage.FakedOutPlayerStore;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class DisguiseCommandHandler implements SubcommandHandler {
    private final MessageService messageService;
    private final FakedOutPlayerStore fakedOutPlayerStore;
    private final ExemptManager exemptManager;
    private final DisguiseCoordinator disguiseCoordinator;
    private final Random random = new Random();

    public DisguiseCommandHandler(MessageService messageService, FakedOutPlayerStore fakedOutPlayerStore,
                                  ExemptManager exemptManager, DisguiseCoordinator disguiseCoordinator) {
        this.messageService = messageService;
        this.fakedOutPlayerStore = fakedOutPlayerStore;
        this.exemptManager = exemptManager;
        this.disguiseCoordinator = disguiseCoordinator;
    }

    @Override
    public String name() {
        return "disguise";
    }

    @Override
    public String permission() {
        return CommandPermissions.DISGUISE;
    }

    @Override
    public boolean execute(CommandSender sender, String[] arguments) {
        if (arguments.length < 1 || arguments.length > 2) {
            messageService.send(sender, "commands.disguise.usage", NamedTextColor.RED);
            return true;
        }

        String targetArgument = arguments[0];
        String customName = arguments.length == 2 ? arguments[1] : null;

        if (targetArgument.equalsIgnoreCase("@a")) {
            if (customName != null) {
                messageService.send(sender, "commands.disguise.usage", NamedTextColor.RED);
                return true;
            }
            disguiseAllOnline(sender);
            return true;
        }

        if (NearbyPlayerResolver.isNearestSelector(targetArgument)) {
            if (!(sender instanceof Player senderPlayer)) {
                messageService.send(sender, "commands.revive.players-only", NamedTextColor.RED);
                return true;
            }

            Optional<Player> nearest = NearbyPlayerResolver.resolveNearest(senderPlayer,
                    candidate -> !exemptManager.isExempt(candidate));
            if (nearest.isEmpty()) {
                messageService.send(sender, "commands.disguise.not-found", NamedTextColor.RED);
                return true;
            }

            disguiseWithRandomIdentity(nearest.get(), sender);
            return true;
        }

        if (NearbyPlayerResolver.isNearSelector(targetArgument)) {
            if (!(sender instanceof Player senderPlayer)) {
                messageService.send(sender, "commands.revive.players-only", NamedTextColor.RED);
                return true;
            }

            disguiseNearby(sender, NearbyPlayerResolver.resolveNear(senderPlayer, candidate -> true));
            return true;
        }

        if (targetArgument.equalsIgnoreCase("@r")) {
            List<Player> eligible = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!exemptManager.isExempt(online)) {
                    eligible.add(online);
                }
            }
            if (eligible.isEmpty()) {
                messageService.send(sender, "commands.disguise.not-found", NamedTextColor.RED);
                return true;
            }

            disguiseWithRandomIdentity(eligible.get(random.nextInt(eligible.size())), sender);
            return true;
        }

        Player target = Bukkit.getPlayer(targetArgument);
        if (target == null) {
            messageService.send(sender, "commands.disguise.not-found", NamedTextColor.RED);
            return true;
        }

        if (customName != null) {
            messageService.send(sender, "commands.revive.fetching-profile", NamedTextColor.GRAY, "name", customName);

            disguiseCoordinator.fetchIdentityAsynchronously(sender, customName, fetchedIdentity -> {
                if (!target.isOnline()) {
                    messageService.send(sender, "commands.disguise.not-found", NamedTextColor.RED);
                    return;
                }
                if (fetchedIdentity.isEmpty()) {
                    messageService.send(sender, "commands.disguise.name-not-found", NamedTextColor.RED, "name",
                            customName);
                    return;
                }
                disguiseCoordinator.applyIdentity(target, fetchedIdentity.get());
                announceSuccess(sender, target, fetchedIdentity.get());
            });
            return true;
        }

        disguiseWithRandomIdentity(target, sender);
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
            Bukkit.getOnlinePlayers().forEach(online -> options.add(online.getName()));
            return TabCompletions.filterByPrefix(options, arguments[0]);
        }
        return List.of();
    }

    private void disguiseNearby(CommandSender sender, List<Player> nearbyPlayers) {
        disguiseGroup(sender, nearbyPlayers, false);
    }

    private void disguiseAllOnline(CommandSender sender) {
        disguiseGroup(sender, Bukkit.getOnlinePlayers(), true);
    }

    private void disguiseGroup(CommandSender sender, Iterable<? extends Player> candidates,
                               boolean skipAlreadyHandled) {
        int disguisedCount = 0;
        int exemptCount = 0;
        int alreadyDisguisedCount = 0;
        int notDisguisedCount = 0;
        boolean poolExhausted = false;

        for (Player candidate : candidates) {
            if (exemptManager.isExempt(candidate)) {
                exemptCount++;
                continue;
            }
            if (skipAlreadyHandled) {
                if (fakedOutPlayerStore.contains(candidate.getUniqueId())) {
                    continue;
                }
                if (disguiseCoordinator.isDisguised(candidate)) {
                    alreadyDisguisedCount++;
                    continue;
                }
            }
            if (poolExhausted || disguiseFromPool(candidate, sender).isEmpty()) {
                poolExhausted = true;
                notDisguisedCount++;
                continue;
            }
            disguisedCount++;
        }

        sendSummary(sender, disguisedCount, exemptCount, alreadyDisguisedCount);
        if (poolExhausted) {
            messageService.send(sender, "commands.disguise.pool-exhausted-all", NamedTextColor.RED, "count",
                    String.valueOf(notDisguisedCount));
        }
    }

    private void sendSummary(CommandSender sender, int disguisedCount, int exemptCount, int alreadyDisguisedCount) {
        messageService.send(sender, "commands.disguise.success-all", NamedTextColor.GREEN, "count",
                String.valueOf(disguisedCount), "exempt", String.valueOf(exemptCount), "already",
                String.valueOf(alreadyDisguisedCount));
    }

    private void disguiseWithRandomIdentity(Player target, CommandSender sender) {
        if (disguiseFromPool(target, sender).isEmpty()) {
            messageService.send(sender, "commands.disguise.pool-exhausted", NamedTextColor.RED);
        }
    }

    private Optional<FakeIdentity> disguiseFromPool(Player target, CommandSender sender) {
        Optional<FakeIdentity> identity = disguiseCoordinator.assignFromPool(target);
        identity.ifPresent(assigned -> announceSuccess(sender, target, assigned));
        return identity;
    }

    private void announceSuccess(CommandSender sender, Player target, FakeIdentity identity) {
        if (!sender.equals(target)) {
            messageService.send(sender, "commands.disguise.success", NamedTextColor.GREEN, "player",
                    target.getName(), "name", identity.name());
        }
    }
}
