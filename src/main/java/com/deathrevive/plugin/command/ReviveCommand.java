package com.deathrevive.plugin.command;

import com.deathrevive.plugin.listener.FakeLeaveListener;
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
        if (!sender.hasPermission("meinplugin.revive")) {
            sender.sendMessage("§cDu hast keine Rechte für diesen Befehl!");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("§cBenutzung: /revive <Spieler> oder /revive @a");
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
                sender.sendMessage("§eEs gab keine Spieler im Spectator-Modus, die wiederbelebt werden konnten.");
            } else {
                sender.sendMessage("§aEs wurden erfolgreich §e" + revivedCount + " §aSpieler wiederbelebt!");
            }
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);

        if (target == null) {
            sender.sendMessage("§cDieser Spieler wurde nicht gefunden.");
            return true;
        }

        if (!fakeLeaveListener.isFakedOut(target.getUniqueId())) {
            sender.sendMessage("§cDieser Spieler ist nicht im Spectator-Modus!");
            return true;
        }

        revivePlayer(target, spawnLocation);
        sender.sendMessage("§aDu hast " + target.getName() + " erfolgreich wiederbelebt!");
        return true;
    }

    private void revivePlayer(Player player, Location location) {
        fakeLeaveListener.clearFakedOut(player.getUniqueId());
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(location);
    }
}
