package com.fakerevive.command;

import com.fakerevive.message.MessageService;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

public class HelpCommandHandler implements SubcommandHandler {
    private final MessageService messageService;

    public HelpCommandHandler(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String permission() {
        return CommandPermissions.NONE;
    }

    @Override
    public boolean execute(CommandSender sender, String[] arguments) {
        messageService.send(sender, "commands.help.header", NamedTextColor.GOLD);
        sendLine(sender, CommandPermissions.REVIVE, "commands.help.revive");
        sendLine(sender, CommandPermissions.DISGUISE, "commands.help.undisguise");
        sendLine(sender, CommandPermissions.DISGUISE, "commands.help.disguise");
        sendLine(sender, CommandPermissions.DISGUISE, "commands.help.exempt");
        sendLine(sender, CommandPermissions.KIT, "commands.help.kit-save");
        sendLine(sender, CommandPermissions.KIT, "commands.help.kit-list");
        sendLine(sender, CommandPermissions.KIT, "commands.help.kit-delete");
        sendLine(sender, CommandPermissions.KIT, "commands.help.kit-give");
        sendLine(sender, CommandPermissions.KIT, "commands.help.kit-equip");
        sendLine(sender, CommandPermissions.TEAM, "commands.help.team");
        sendLine(sender, CommandPermissions.ARMOR, "commands.help.armor");
        sendLine(sender, CommandPermissions.POOL, "commands.help.pool");
        sendLine(sender, CommandPermissions.LEAVE, "commands.help.leave");
        sendLine(sender, CommandPermissions.RELOAD, "commands.help.reload");
        messageService.send(sender, "commands.help.help", NamedTextColor.YELLOW);
        return true;
    }

    private void sendLine(CommandSender sender, String permission, String messageKey) {
        if (sender.hasPermission(permission)) {
            messageService.send(sender, messageKey, NamedTextColor.YELLOW);
        }
    }
}
