package com.fakerevive.command;

import com.fakerevive.kit.KitManager;
import com.fakerevive.message.MessageService;
import com.fakerevive.storage.EntryNameValidator;
import com.fakerevive.team.TeamManager;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class KitCommandHandler implements SubcommandHandler {
    private static final int ARMOR_SLOTS = 4;
    private static final String CLEAR_BEFORE_GIVE = "kits.clear-before-give";
    private static final String CLEAR_BEFORE_EQUIP = "kits.clear-before-equip";

    private final JavaPlugin plugin;
    private final MessageService messageService;
    private final KitManager kitManager;
    private final TeamManager teamManager;
    private final TargetResolver targetResolver;

    public KitCommandHandler(JavaPlugin plugin, MessageService messageService, KitManager kitManager,
                             TeamManager teamManager, TargetResolver targetResolver) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.kitManager = kitManager;
        this.teamManager = teamManager;
        this.targetResolver = targetResolver;
    }

    @Override
    public String name() {
        return "kit";
    }

    @Override
    public String permission() {
        return CommandPermissions.KIT;
    }

    @Override
    public boolean execute(CommandSender sender, String[] arguments) {
        if (arguments.length < 1) {
            messageService.send(sender, "commands.kit.usage", NamedTextColor.RED);
            return true;
        }

        String action = arguments[0].toLowerCase();
        return switch (action) {
            case "save" -> handleSave(sender, arguments);
            case "list" -> handleList(sender, arguments);
            case "give" -> handleGive(sender, arguments);
            case "equip" -> handleEquip(sender, arguments);
            case "delete" -> handleDelete(sender, arguments);
            default -> {
                messageService.send(sender, "commands.kit.usage", NamedTextColor.RED);
                yield true;
            }
        };
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] arguments) {
        if (arguments.length == 1) {
            return TabCompletions.filterByPrefix(List.of("save", "list", "give", "equip", "delete"), arguments[0]);
        }
        if (arguments.length < 2) {
            return List.of();
        }
        return switch (arguments[0].toLowerCase()) {
            case "give" -> completeGive(arguments);
            case "equip" -> completeEquip(arguments);
            case "delete" -> arguments.length == 2
                    ? TabCompletions.filterByPrefix(new ArrayList<>(kitManager.getKitNames()), arguments[1])
                    : List.of();
            default -> List.of();
        };
    }

    private List<String> completeGive(String[] arguments) {
        if (arguments.length == 2) {
            return TabCompletions.filterByPrefix(new ArrayList<>(kitManager.getKitNames()), arguments[1]);
        }
        if (arguments.length == 3) {
            return TabCompletions.filterByPrefix(targetResolver.completionOptions(true, true), arguments[2]);
        }
        return List.of();
    }

    private List<String> completeEquip(String[] arguments) {
        if (arguments.length == 2) {
            List<String> options = new ArrayList<>(kitManager.getKitNames());
            options.addAll(teamManager.getTeamNames());
            return TabCompletions.filterByPrefix(options, arguments[1]);
        }
        if (arguments.length == 3) {
            return TabCompletions.filterByPrefix(targetResolver.completionOptions(true, false), arguments[2]);
        }
        return List.of();
    }

    private boolean handleSave(CommandSender sender, String[] arguments) {
        if (arguments.length != 2) {
            messageService.send(sender, "commands.kit.usage", NamedTextColor.RED);
            return true;
        }

        if (!(sender instanceof Player player)) {
            messageService.send(sender, "commands.kit.players-only", NamedTextColor.RED);
            return true;
        }

        String kitName = arguments[1];
        if (!EntryNameValidator.isValid(kitName)) {
            messageService.send(sender, "commands.kit.invalid-name", NamedTextColor.RED, "name", kitName);
            return true;
        }
        kitManager.saveKit(kitName, player);
        messageService.send(sender, "commands.kit.saved", NamedTextColor.GREEN, "name", kitName);
        return true;
    }

    private boolean handleList(CommandSender sender, String[] arguments) {
        if (arguments.length != 1) {
            messageService.send(sender, "commands.kit.usage", NamedTextColor.RED);
            return true;
        }

        Set<String> kitNames = kitManager.getKitNames();
        if (kitNames.isEmpty()) {
            messageService.send(sender, "commands.kit.list-empty", NamedTextColor.YELLOW);
        } else {
            messageService.send(sender, "commands.kit.list-format", NamedTextColor.GREEN, "kits",
                    String.join(", ", kitNames));
        }
        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] arguments) {
        if (arguments.length != 2) {
            messageService.send(sender, "commands.kit.delete-usage", NamedTextColor.RED);
            return true;
        }

        if (!kitManager.deleteKit(arguments[1])) {
            messageService.send(sender, "commands.kit.delete-not-found", NamedTextColor.RED, "name", arguments[1]);
            return true;
        }

        messageService.send(sender, "commands.kit.deleted", NamedTextColor.GREEN, "name", arguments[1]);
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] arguments) {
        if (arguments.length != 3) {
            messageService.send(sender, "commands.kit.give-usage", NamedTextColor.RED);
            return true;
        }

        String kitName = arguments[1];
        String targetArgument = arguments[2];

        if (targetResolver.isAllSelector(targetArgument)) {
            if (!kitManager.getKitNames().contains(kitName)) {
                messageService.send(sender, "commands.kit.give-kit-not-found", NamedTextColor.RED, "name", kitName);
                return true;
            }

            int count = 0;
            for (Player online : targetResolver.resolveAllOnline()) {
                clearIfConfigured(online, CLEAR_BEFORE_GIVE, false);
                kitManager.giveKit(kitName, online);
                count++;
            }

            messageService.send(sender, "commands.kit.give-all-success", NamedTextColor.GREEN, "name", kitName,
                    "count", String.valueOf(count));
            return true;
        }

        if (targetResolver.isTeam(targetArgument)) {
            if (!kitManager.getKitNames().contains(kitName)) {
                messageService.send(sender, "commands.kit.give-kit-not-found", NamedTextColor.RED, "name", kitName);
                return true;
            }

            int count = 0;
            for (Player member : targetResolver.resolveOnlineTeamMembers(targetArgument)) {
                clearIfConfigured(member, CLEAR_BEFORE_GIVE, false);
                kitManager.giveKit(kitName, member);
                count++;
            }

            messageService.send(sender, "commands.kit.give-team-success", NamedTextColor.GREEN, "name", kitName,
                    "count", String.valueOf(count), "team", targetArgument);
            return true;
        }

        Optional<Player> resolvedTarget = targetResolver.resolvePlayerOrFakeName(targetArgument);
        if (resolvedTarget.isEmpty()) {
            messageService.send(sender, "commands.kit.give-player-not-found", NamedTextColor.RED, "player",
                    targetArgument);
            return true;
        }

        Player target = resolvedTarget.get();
        clearIfConfigured(target, CLEAR_BEFORE_GIVE, false);

        if (!kitManager.giveKit(kitName, target)) {
            messageService.send(sender, "commands.kit.give-kit-not-found", NamedTextColor.RED, "name", kitName);
            return true;
        }

        messageService.send(sender, "commands.kit.give-success", NamedTextColor.GREEN, "name", kitName, "player",
                target.getName());
        return true;
    }

    private boolean handleEquip(CommandSender sender, String[] arguments) {
        if (arguments.length < 2 || arguments.length > 3) {
            messageService.send(sender, "commands.kit.equip-usage", NamedTextColor.RED);
            return true;
        }

        String kitName = arguments[1];
        Player target;
        boolean equippingOther;
        if (arguments.length == 3) {
            String targetArgument = arguments[2];
            if (targetResolver.isAllSelector(targetArgument)) {
                int equipped = 0;
                for (Player online : targetResolver.resolveAllOnline()) {
                    clearIfConfigured(online, CLEAR_BEFORE_EQUIP, true);
                    if (kitManager.equipKit(kitName, online)) {
                        equipped++;
                    }
                }
                messageService.send(sender, "commands.kit.equip-all-success", NamedTextColor.GREEN, "name", kitName,
                        "count", String.valueOf(equipped));
                return true;
            }

            if (targetResolver.isTeam(targetArgument)) {
                return equipTeam(sender, kitName, targetArgument);
            }

            Optional<Player> resolvedTarget = targetResolver.resolvePlayer(targetArgument);
            if (resolvedTarget.isEmpty()) {
                messageService.send(sender, "commands.kit.equip-player-not-found", NamedTextColor.RED, "player",
                        targetArgument);
                return true;
            }
            target = resolvedTarget.get();
            equippingOther = true;
        } else {
            if (targetResolver.isTeam(kitName)) {
                return equipTeamKit(sender, kitName);
            }

            if (!(sender instanceof Player senderPlayer)) {
                messageService.send(sender, "commands.kit.players-only", NamedTextColor.RED);
                return true;
            }
            target = senderPlayer;
            equippingOther = false;
        }

        clearIfConfigured(target, CLEAR_BEFORE_EQUIP, true);

        if (!kitManager.equipKit(kitName, target)) {
            messageService.send(sender, "commands.kit.equip-kit-not-found", NamedTextColor.RED, "name", kitName);
            return true;
        }

        if (equippingOther) {
            messageService.send(sender, "commands.kit.equip-success-other", NamedTextColor.GREEN, "name", kitName,
                    "player", target.getName());
        } else {
            messageService.send(sender, "commands.kit.equip-success", NamedTextColor.GREEN, "name", kitName);
        }
        return true;
    }

    private boolean equipTeamKit(CommandSender sender, String teamName) {
        String teamKit = teamManager.getTeamKit(teamName).orElse(null);
        if (teamKit == null) {
            messageService.send(sender, "commands.kit.equip-team-no-kit", NamedTextColor.RED, "team", teamName);
            return true;
        }

        if (!kitManager.getKitNames().contains(teamKit)) {
            messageService.send(sender, "commands.kit.equip-kit-not-found", NamedTextColor.RED, "name", teamKit);
            return true;
        }

        return equipTeam(sender, teamKit, teamName);
    }

    private boolean equipTeam(CommandSender sender, String kitName, String teamName) {
        int equipped = 0;
        for (Player member : targetResolver.resolveOnlineTeamMembers(teamName)) {
            clearIfConfigured(member, CLEAR_BEFORE_EQUIP, true);
            if (kitManager.equipKit(kitName, member)) {
                equipped++;
            }
        }

        messageService.send(sender, "commands.kit.equip-team-success", NamedTextColor.GREEN, "name", kitName,
                "count", String.valueOf(equipped), "team", teamName);
        return true;
    }

    private void clearIfConfigured(Player player, String configurationKey, boolean includeEquipmentSlots) {
        if (!plugin.getConfig().getBoolean(configurationKey, false)) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        if (includeEquipmentSlots) {
            inventory.setArmorContents(new ItemStack[ARMOR_SLOTS]);
            inventory.setItemInOffHand(null);
        }
    }
}
