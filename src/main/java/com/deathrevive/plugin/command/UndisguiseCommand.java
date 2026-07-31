package com.deathrevive.plugin.command;

import com.deathrevive.plugin.disguise.ActiveDisguiseRegistry;
import com.deathrevive.plugin.disguise.PlayerDisguiseService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class UndisguiseCommand implements CommandExecutor {

    private final ActiveDisguiseRegistry activeDisguiseRegistry;
    private final PlayerDisguiseService playerDisguiseService;

    public UndisguiseCommand(ActiveDisguiseRegistry activeDisguiseRegistry, PlayerDisguiseService playerDisguiseService) {
        this.activeDisguiseRegistry = activeDisguiseRegistry;
        this.playerDisguiseService = playerDisguiseService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player target;
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(Component.text("Benutzung: /undisguise [Spieler]", NamedTextColor.RED));
                return true;
            }
            target = (Player) sender;
        } else {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("Dieser Spieler wurde nicht gefunden.", NamedTextColor.RED));
                return true;
            }
        }

        if (activeDisguiseRegistry.getIdentity(target.getUniqueId()).isEmpty()) {
            sender.sendMessage(Component.text("Dieser Spieler ist nicht verkleidet.", NamedTextColor.RED));
            return true;
        }

        activeDisguiseRegistry.clear(target.getUniqueId());
        playerDisguiseService.remove(target);
        target.sendMessage(Component.text("Deine Verkleidung wurde entfernt.", NamedTextColor.YELLOW));

        if (sender != target) {
            sender.sendMessage(Component.text("Du hast " + target.getName() + " die Verkleidung entfernt.", NamedTextColor.GREEN));
        }

        return true;
    }
}
