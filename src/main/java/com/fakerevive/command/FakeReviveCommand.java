package com.fakerevive.command;

import com.fakerevive.message.MessageService;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FakeReviveCommand implements CommandExecutor, TabCompleter {
    private static final String LEAVE_SUBCOMMAND = "leave";

    private final MessageService messageService;
    private final Map<String, SubcommandHandler> handlersByName = new LinkedHashMap<>();

    public FakeReviveCommand(MessageService messageService, List<SubcommandHandler> handlers) {
        this.messageService = messageService;
        for (SubcommandHandler handler : handlers) {
            handlersByName.put(handler.name(), handler);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            messageService.send(sender, "commands.fr.usage", NamedTextColor.RED);
            return true;
        }

        String[] rest = Arrays.copyOfRange(args, 1, args.length);

        if (args[0].equalsIgnoreCase(LEAVE_SUBCOMMAND)) {
            return handlersByName.get(LEAVE_SUBCOMMAND).execute(sender, rest);
        }

        SubcommandHandler handler = handlersByName.get(args[0].toLowerCase());
        if (handler == null) {
            messageService.send(sender, "commands.fr.usage", NamedTextColor.RED);
            return true;
        }

        if (!canUse(sender, handler)) {
            messageService.sendWithoutPrefix(sender, "commands.fr.no-permission", NamedTextColor.RED);
            return true;
        }

        return handler.execute(sender, rest);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> allowedSubcommands = handlersByName.values().stream()
                    .filter(handler -> canUse(sender, handler))
                    .map(SubcommandHandler::name)
                    .collect(Collectors.toList());
            return TabCompletions.filterByPrefix(allowedSubcommands, args[0]);
        }

        SubcommandHandler handler = handlersByName.get(args[0].toLowerCase());
        if (handler == null || !canUse(sender, handler)) {
            return List.of();
        }

        return handler.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
    }

    private boolean canUse(CommandSender sender, SubcommandHandler handler) {
        if (handler.permission().isEmpty()) {
            return CommandPermissions.ALL.stream().anyMatch(sender::hasPermission);
        }
        return sender.hasPermission(handler.permission());
    }
}
