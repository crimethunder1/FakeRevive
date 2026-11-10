package com.fakerevive.command;

import com.fakerevive.message.MessageService;
import com.fakerevive.team.TeamManager;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;

public class LeaveCommandHandler implements SubcommandHandler {
    private final MessageService messageService;
    private final TeamManager teamManager;

    public LeaveCommandHandler(MessageService messageService, TeamManager teamManager) {
        this.messageService = messageService;
        this.teamManager = teamManager;
    }

    @Override
    public String name() {
        return "leave";
    }

    @Override
    public String permission() {
        return CommandPermissions.LEAVE;
    }

    @Override
    public boolean execute(CommandSender sender, String[] arguments) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "commands.leave.players-only", NamedTextColor.RED);
            return true;
        }

        if (!player.hasPermission(CommandPermissions.LEAVE)) {
            messageService.send(player, "commands.leave.no-permission", NamedTextColor.RED);
            return true;
        }

        Optional<String> team = teamManager.getPlayerTeam(player.getUniqueId());
        if (team.isEmpty()) {
            messageService.send(player, "commands.leave.no-team", NamedTextColor.RED);
            return true;
        }

        teamManager.removePlayer(player.getUniqueId());
        messageService.send(player, "commands.leave.success", NamedTextColor.YELLOW, "team", team.get());
        return true;
    }
}
