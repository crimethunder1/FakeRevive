package com.fakerevive.command;

import com.fakerevive.message.MessageService;
import com.fakerevive.team.TeamGui;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TeamCommandHandler implements SubcommandHandler {
    private final MessageService messageService;
    private final TeamGui teamGui;

    public TeamCommandHandler(MessageService messageService, TeamGui teamGui) {
        this.messageService = messageService;
        this.teamGui = teamGui;
    }

    @Override
    public String name() {
        return "team";
    }

    @Override
    public String permission() {
        return CommandPermissions.TEAM;
    }

    @Override
    public boolean execute(CommandSender sender, String[] arguments) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "commands.team.players-only", NamedTextColor.RED);
            return true;
        }

        teamGui.openMainMenu(player);
        return true;
    }
}
