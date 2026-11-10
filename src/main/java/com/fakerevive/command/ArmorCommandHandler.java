package com.fakerevive.command;

import com.fakerevive.armor.ArmorLockManager;
import com.fakerevive.message.MessageService;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ArmorCommandHandler implements SubcommandHandler {
    private final MessageService messageService;
    private final ArmorLockManager armorLockManager;
    private final TargetResolver targetResolver;

    public ArmorCommandHandler(MessageService messageService, ArmorLockManager armorLockManager,
                               TargetResolver targetResolver) {
        this.messageService = messageService;
        this.armorLockManager = armorLockManager;
        this.targetResolver = targetResolver;
    }

    @Override
    public String name() {
        return "armor";
    }

    @Override
    public String permission() {
        return CommandPermissions.ARMOR;
    }

    @Override
    public boolean execute(CommandSender sender, String[] arguments) {
        if (arguments.length < 1 || arguments.length > 2) {
            messageService.send(sender, "commands.armor.usage", NamedTextColor.RED);
            return true;
        }

        String action = arguments[0].toLowerCase();
        if (!action.equals("on") && !action.equals("off") && !action.equals("status")) {
            messageService.send(sender, "commands.armor.usage", NamedTextColor.RED);
            return true;
        }

        if (action.equals("status")) {
            return handleStatus(sender, arguments.length == 2 ? arguments[1] : null);
        }

        boolean lock = action.equals("on");

        if (arguments.length == 1) {
            if (!(sender instanceof Player player)) {
                messageService.send(sender, "commands.armor.players-only", NamedTextColor.RED);
                return true;
            }

            setArmorLock(player.getUniqueId(), lock);
            messageService.send(sender, lock ? "commands.armor.on-self" : "commands.armor.off-self",
                    NamedTextColor.GREEN);
            return true;
        }

        String targetArgument = arguments[1];

        if (targetResolver.isAllSelector(targetArgument)) {
            int count = 0;
            for (Player online : targetResolver.resolveAllOnline()) {
                setArmorLock(online.getUniqueId(), lock);
                count++;
            }
            messageService.send(sender, lock ? "commands.armor.on-all" : "commands.armor.off-all",
                    NamedTextColor.GREEN, "count", String.valueOf(count));
            return true;
        }

        if (targetResolver.isTeam(targetArgument)) {
            int count = 0;
            for (UUID memberId : targetResolver.resolveTeamMemberIds(targetArgument)) {
                setArmorLock(memberId, lock);
                count++;
            }
            messageService.send(sender, lock ? "commands.armor.on-team" : "commands.armor.off-team",
                    NamedTextColor.GREEN, "count", String.valueOf(count), "team", targetArgument);
            return true;
        }

        Optional<Player> target = targetResolver.resolvePlayer(targetArgument);
        if (target.isEmpty()) {
            messageService.send(sender, "commands.armor.player-not-found", NamedTextColor.RED, "player",
                    targetArgument);
            return true;
        }

        setArmorLock(target.get().getUniqueId(), lock);
        messageService.send(sender, lock ? "commands.armor.on-other" : "commands.armor.off-other",
                NamedTextColor.GREEN, "player", target.get().getName());
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] arguments) {
        if (arguments.length == 1) {
            return TabCompletions.filterByPrefix(List.of("on", "off", "status"), arguments[0]);
        }
        if (arguments.length == 2) {
            boolean includeGroups = !arguments[0].equalsIgnoreCase("status");
            return TabCompletions.filterByPrefix(targetResolver.completionOptions(includeGroups, false),
                    arguments[1]);
        }
        return List.of();
    }

    private boolean handleStatus(CommandSender sender, @Nullable String targetArgument) {
        if (targetArgument != null) {
            Optional<Player> target = targetResolver.resolvePlayer(targetArgument);
            if (target.isEmpty()) {
                messageService.send(sender, "commands.armor.player-not-found", NamedTextColor.RED, "player",
                        targetArgument);
                return true;
            }

            boolean locked = armorLockManager.isLocked(target.get().getUniqueId());
            messageService.send(sender, locked ? "commands.armor.status-locked" : "commands.armor.status-unlocked",
                    locked ? NamedTextColor.YELLOW : NamedTextColor.GREEN, "player", target.get().getName());
            return true;
        }

        List<String> names = new ArrayList<>();
        for (UUID lockedId : armorLockManager.getLocked()) {
            Player online = Bukkit.getPlayer(lockedId);
            names.add(online != null ? online.getName() : lockedId.toString());
        }

        if (names.isEmpty()) {
            messageService.send(sender, "commands.armor.status-none", NamedTextColor.YELLOW);
            return true;
        }

        messageService.send(sender, "commands.armor.status-list", NamedTextColor.GREEN, "players",
                String.join(", ", names));
        return true;
    }

    private void setArmorLock(UUID playerId, boolean lock) {
        if (lock) {
            armorLockManager.lock(playerId);
        } else {
            armorLockManager.unlock(playerId);
        }
    }
}
