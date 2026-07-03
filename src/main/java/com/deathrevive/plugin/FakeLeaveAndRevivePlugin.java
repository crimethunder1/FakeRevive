package com.deathrevive.plugin;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class FakeLeaveAndRevivePlugin extends JavaPlugin implements Listener, CommandExecutor {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        this.getCommand("revive").setExecutor(this);
        getLogger().info("Fake-Leave & Revive (Paper-Native) erfolgreich aktiviert!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Fake-Leave & Revive Plugin deactivated.");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location deathLocation = player.getLocation();
        String originalDeathMessage = event.getDeathMessage();

        if (originalDeathMessage != null) {
            Bukkit.broadcastMessage(originalDeathMessage);
        }

        event.setDeathMessage(null);

        String leaveMessage = "§e" + player.getName() + " left the game";
        Bukkit.broadcastMessage(leaveMessage);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            player.spigot().respawn();
            player.setGameMode(GameMode.SPECTATOR);
            player.teleport(deathLocation);
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode() == GameMode.SPECTATOR) {
            event.quitMessage(null);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("revive")) {

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
                int count = 0;

                for (Player target : Bukkit.getOnlinePlayers()) {
                    if (target.getGameMode() == GameMode.SPECTATOR) {
                        revivePlayer(target, spawnLocation);
                        count++;
                    }
                }

                if (count == 0) {
                    sender.sendMessage("§eEs gab keine Spieler im Spectator-Modus, die wiederbelebt werden konnten.");
                } else {
                    sender.sendMessage("§aEs wurden erfolgreich §e" + count + " §aSpieler wiederbelebt!");
                }
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);

            if (target == null) {
                sender.sendMessage("§cDieser Spieler wurde nicht gefunden.");
                return true;
            }

            if (target.getGameMode() != GameMode.SPECTATOR) {
                sender.sendMessage("§cDieser Spieler ist nicht im Spectator-Modus!");
                return true;
            }

            revivePlayer(target, spawnLocation);
            sender.sendMessage("§aDu hast " + target.getName() + " erfolgreich wiederbelebt!");
            return true;
        }
        return false;
    }

    private void revivePlayer(Player player, Location loc) {
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(loc);
    }
}
