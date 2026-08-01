package com.deathrevive.plugin.command;

import com.deathrevive.plugin.kit.KitManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.stream.Collectors;

public class KitCommand implements CommandExecutor {

    private final KitManager kitManager;

    public KitCommand(KitManager kitManager) {
        this.kitManager = kitManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage(Component.text("Benutzung: /kit save <Name> | /kit list", NamedTextColor.RED));
            return true;
        }

        if (args[0].equalsIgnoreCase("save")) {
            if (args.length != 2) {
                sender.sendMessage(Component.text("Benutzung: /kit save <Name> | /kit list", NamedTextColor.RED));
                return true;
            }

            if (!(sender instanceof Player)) {
                sender.sendMessage(Component.text("Nur Spieler können Kits speichern.", NamedTextColor.RED));
                return true;
            }

            String name = args[1];
            kitManager.saveKit(name, (Player) sender);
            sender.sendMessage(Component.text("Kit \"" + name + "\" wurde gespeichert.", NamedTextColor.GREEN));
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            if (args.length != 1) {
                sender.sendMessage(Component.text("Benutzung: /kit save <Name> | /kit list", NamedTextColor.RED));
                return true;
            }

            Set<String> kitNames = kitManager.getKitNames();
            if (kitNames.isEmpty()) {
                sender.sendMessage(Component.text("Keine Kits vorhanden.", NamedTextColor.YELLOW));
            } else {
                sender.sendMessage(Component.text(
                        "Verfügbare Kits: " + kitNames.stream().collect(Collectors.joining(", ")),
                        NamedTextColor.GREEN));
            }
            return true;
        }

        sender.sendMessage(Component.text("Benutzung: /kit save <Name> | /kit list", NamedTextColor.RED));
        return true;
    }
}
