package com.fakerevive.command;

import com.fakerevive.exempt.ExemptManager;
import com.fakerevive.message.MessageService;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ExemptCommandHandler implements SubcommandHandler {
    private final MessageService messageService;
    private final ExemptManager exemptManager;

    public ExemptCommandHandler(MessageService messageService, ExemptManager exemptManager) {
        this.messageService = messageService;
        this.exemptManager = exemptManager;
    }

    @Override
    public String name() {
        return "exempt";
    }

    @Override
    public String permission() {
        return CommandPermissions.DISGUISE;
    }

    @Override
    public boolean execute(CommandSender sender, String[] arguments) {
        if (arguments.length == 1 && arguments[0].equalsIgnoreCase("list")) {
            return handleList(sender);
        }

        if (arguments.length != 2) {
            sendUsage(sender);
            return true;
        }

        String action = arguments[0].toLowerCase();
        String playerName = arguments[1];
        if (action.equals("add")) {
            return handleAdd(sender, playerName);
        }
        if (action.equals("remove")) {
            return handleRemove(sender, playerName);
        }

        sendUsage(sender);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] arguments) {
        if (arguments.length == 1) {
            return TabCompletions.filterByPrefix(List.of("add", "remove", "list"), arguments[0]);
        }
        if (arguments.length == 2) {
            return switch (arguments[0].toLowerCase()) {
                case "add" -> {
                    List<String> options = new ArrayList<>();
                    Bukkit.getOnlinePlayers().forEach(online -> options.add(online.getName()));
                    yield TabCompletions.filterByPrefix(options, arguments[1]);
                }
                case "remove" -> TabCompletions.filterByPrefix(
                        new ArrayList<>(exemptManager.getStoredPlayers().values()), arguments[1]);
                default -> List.of();
            };
        }
        return List.of();
    }

    private boolean handleAdd(CommandSender sender, String playerName) {
        OfflinePlayer target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            target = Bukkit.getOfflinePlayerIfCached(playerName);
        }
        if (target == null || target.getName() == null) {
            messageService.send(sender, "commands.exempt.player-not-found", NamedTextColor.RED, "player", playerName);
            return true;
        }

        String targetName = target.getName();
        if (!exemptManager.add(target.getUniqueId(), targetName)) {
            messageService.send(sender, "commands.exempt.already-exempt", NamedTextColor.YELLOW, "player", targetName);
            return true;
        }

        messageService.send(sender, "commands.exempt.added", NamedTextColor.GREEN, "player", targetName);
        return true;
    }

    private boolean handleRemove(CommandSender sender, String playerName) {
        Optional<UUID> storedPlayerId = exemptManager.findStoredByName(playerName);
        if (storedPlayerId.isEmpty()) {
            Player online = Bukkit.getPlayerExact(playerName);
            if (online != null && exemptManager.isStored(online.getUniqueId())) {
                storedPlayerId = Optional.of(online.getUniqueId());
            }
        }

        if (storedPlayerId.isPresent()) {
            String storedName = exemptManager.getStoredPlayers().getOrDefault(storedPlayerId.get(), playerName);
            exemptManager.remove(storedPlayerId.get());
            messageService.send(sender, "commands.exempt.removed", NamedTextColor.GREEN, "player", storedName);
            return true;
        }

        if (exemptManager.isConfigured(playerName)) {
            messageService.send(sender, "commands.exempt.config-only", NamedTextColor.YELLOW, "player", playerName);
            return true;
        }

        messageService.send(sender, "commands.exempt.not-exempt", NamedTextColor.RED, "player", playerName);
        return true;
    }

    private boolean handleList(CommandSender sender) {
        List<String> names = new ArrayList<>(exemptManager.getStoredPlayers().values());
        for (String configuredName : exemptManager.getConfiguredNames()) {
            names.add(configuredName + messageService.get("commands.exempt.config-suffix"));
        }

        if (names.isEmpty()) {
            messageService.send(sender, "commands.exempt.list-empty", NamedTextColor.YELLOW);
            return true;
        }

        messageService.send(sender, "commands.exempt.list-format", NamedTextColor.GREEN, "players",
                String.join(", ", names));
        return true;
    }

    private void sendUsage(CommandSender sender) {
        messageService.send(sender, "commands.exempt.usage", NamedTextColor.RED);
    }
}
