package com.deathrevive.plugin.command;

import com.deathrevive.plugin.kit.KitManager;
import com.deathrevive.plugin.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

public class KitCommand implements CommandExecutor {

    private final KitManager kitManager;
    private final MessageService messageService;
    private final JavaPlugin plugin;

    public KitCommand(KitManager kitManager, MessageService messageService, JavaPlugin plugin) {
        this.kitManager = kitManager;
        this.messageService = messageService;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
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

        if (args[0].equalsIgnoreCase("give")) {
            return handleGive(sender, args);
        }

        if (args[0].equalsIgnoreCase("equip")) {
            return handleEquip(sender, args);
        }

        sender.sendMessage(Component.text(messageService.get("commands.kit.usage"), NamedTextColor.RED));
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(Component.text(messageService.get("commands.kit.give-usage"), NamedTextColor.RED));
            return true;
        }

        String name = args[1];
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(Component.text(
                    messageService.get("commands.kit.give-player-not-found", "player", args[2]), NamedTextColor.RED));
            return true;
        }

        if (plugin.getConfig().getBoolean("kits.clear-before-give", false)) {
            target.getInventory().clear();
        }

        if (!kitManager.giveKit(name, target)) {
            sender.sendMessage(Component.text(
                    messageService.get("commands.kit.give-kit-not-found", "name", name), NamedTextColor.RED));
            return true;
        }

        sender.sendMessage(Component.text(
                messageService.get("commands.kit.give-success", "name", name, "player", target.getName()),
                NamedTextColor.GREEN));
        return true;
    }

    private boolean handleEquip(CommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage(Component.text(messageService.get("commands.kit.equip-usage"), NamedTextColor.RED));
            return true;
        }

        String name = args[1];
        Player target;
        boolean equippingOther;
        if (args.length == 3) {
            target = Bukkit.getPlayer(args[2]);
            equippingOther = true;
            if (target == null) {
                sender.sendMessage(Component.text(
                        messageService.get("commands.kit.equip-player-not-found", "player", args[2]), NamedTextColor.RED));
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(Component.text(messageService.get("commands.kit.players-only"), NamedTextColor.RED));
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
            sender.sendMessage(Component.text(
                    messageService.get("commands.kit.equip-kit-not-found", "name", name), NamedTextColor.RED));
            return true;
        }

        if (equippingOther) {
            sender.sendMessage(Component.text(
                    messageService.get("commands.kit.equip-success-other", "name", name, "player", target.getName()),
                    NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text(
                    messageService.get("commands.kit.equip-success", "name", name), NamedTextColor.GREEN));
        }
        return true;
    }
}
