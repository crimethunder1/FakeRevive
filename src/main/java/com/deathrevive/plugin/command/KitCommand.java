package com.deathrevive.plugin.command;

import com.deathrevive.plugin.kit.KitManager;
import com.deathrevive.plugin.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;

public class KitCommand implements CommandExecutor {

    private final KitManager kitManager;
    private final MessageService messageService;

    public KitCommand(KitManager kitManager, MessageService messageService) {
        this.kitManager = kitManager;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1 || args.length > 2) {
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

        sender.sendMessage(Component.text(messageService.get("commands.kit.usage"), NamedTextColor.RED));
        return true;
    }
}
