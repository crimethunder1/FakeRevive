package com.deathrevive.plugin.command;

import com.deathrevive.plugin.listener.FakeLeaveListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ReviveCommand implements CommandExecutor {

    private final FakeLeaveListener fakeLeaveListener;

    public ReviveCommand(FakeLeaveListener fakeLeaveListener) {
        this.fakeLeaveListener = fakeLeaveListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("fakeleaveandrevive.revive")) {
            sender.sendMessage(Component.text("Du hast keine Rechte für diesen Befehl!", NamedTextColor.RED));
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(Component.text("Benutzung: /revive <Spieler> oder /revive @a", NamedTextColor.RED));
            return true;
        }

        Location spawnLocation;
        if (sender instanceof Player) {
            spawnLocation = ((Player) sender).getLocation();
        } else {
            spawnLocation = Bukkit.getWorlds().get(0).getSpawnLocation();
        }

        if (args[0].equalsIgnoreCase("@a")) {
            int revivedCount = 0;

            for (Player target : Bukkit.getOnlinePlayers()) {
                if (fakeLeaveListener.isFakedOut(target.getUniqueId())) {
                    revivePlayer(target, spawnLocation);
                    revivedCount++;
                }
            }

            if (revivedCount == 0) {
                sender.sendMessage(Component.text(
                        "Es gab keine Spieler im Spectator-Modus, die wiederbelebt werden konnten.",
                        NamedTextColor.YELLOW));
            } else {
                sender.sendMessage(Component.text("Es wurden erfolgreich ", NamedTextColor.GREEN)
                        .append(Component.text(revivedCount, NamedTextColor.YELLOW))
                        .append(Component.text(" Spieler wiederbelebt!", NamedTextColor.GREEN)));
            }
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);

        if (target == null) {
            sender.sendMessage(Component.text("Dieser Spieler wurde nicht gefunden.", NamedTextColor.RED));
            return true;
        }

        if (!fakeLeaveListener.isFakedOut(target.getUniqueId())) {
            sender.sendMessage(Component.text("Dieser Spieler ist nicht im Spectator-Modus!", NamedTextColor.RED));
            return true;
        }

        revivePlayer(target, spawnLocation);
        sender.sendMessage(Component.text("Du hast " + target.getName() + " erfolgreich wiederbelebt!", NamedTextColor.GREEN));
        return true;
    }

    private void revivePlayer(Player player, Location location) {
        fakeLeaveListener.clearFakedOut(player.getUniqueId());
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(location);
    }
}
