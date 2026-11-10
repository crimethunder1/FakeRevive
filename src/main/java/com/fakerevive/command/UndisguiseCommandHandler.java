package com.fakerevive.command;

import com.fakerevive.disguise.ActiveDisguiseRegistry;
import com.fakerevive.disguise.DisguiseCoordinator;
import com.fakerevive.message.MessageService;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UndisguiseCommandHandler implements SubcommandHandler {
    private final MessageService messageService;
    private final ActiveDisguiseRegistry activeDisguiseRegistry;
    private final DisguiseCoordinator disguiseCoordinator;

    public UndisguiseCommandHandler(MessageService messageService, ActiveDisguiseRegistry activeDisguiseRegistry,
                                    DisguiseCoordinator disguiseCoordinator) {
        this.messageService = messageService;
        this.activeDisguiseRegistry = activeDisguiseRegistry;
        this.disguiseCoordinator = disguiseCoordinator;
    }

    @Override
    public String name() {
        return "undisguise";
    }

    @Override
    public String permission() {
        return CommandPermissions.DISGUISE;
    }

    @Override
    public boolean execute(CommandSender sender, String[] arguments) {
        if (arguments.length == 1 && arguments[0].equalsIgnoreCase("@a")) {
            int count = 0;
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (disguiseCoordinator.isDisguised(online)) {
                    disguiseCoordinator.remove(online);
                    messageService.send(online, "commands.undisguise.success-self", NamedTextColor.YELLOW);
                    count++;
                }
            }
            messageService.send(sender, "commands.undisguise.success-all", NamedTextColor.GREEN, "count",
                    String.valueOf(count));
            return true;
        }

        Player target;
        if (arguments.length == 0) {
            if (!(sender instanceof Player senderPlayer)) {
                messageService.send(sender, "commands.undisguise.usage", NamedTextColor.RED);
                return true;
            }
            target = senderPlayer;
        } else {
            target = Bukkit.getPlayer(arguments[0]);
            if (target == null) {
                UUID byFakeName = activeDisguiseRegistry.findByFakeName(arguments[0]).orElse(null);
                if (byFakeName != null) {
                    target = Bukkit.getPlayer(byFakeName);
                }
            }
            if (target == null) {
                messageService.send(sender, "commands.undisguise.not-found", NamedTextColor.RED);
                return true;
            }
        }

        if (!disguiseCoordinator.isDisguised(target)) {
            messageService.send(sender, "commands.undisguise.not-disguised", NamedTextColor.RED);
            return true;
        }

        disguiseCoordinator.remove(target);
        messageService.send(target, "commands.undisguise.success-self", NamedTextColor.YELLOW);

        if (sender != target) {
            messageService.send(sender, "commands.undisguise.success-other", NamedTextColor.GREEN, "player",
                    target.getName());
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] arguments) {
        if (arguments.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("@a");
            Bukkit.getOnlinePlayers().forEach(online -> {
                options.add(online.getName());
                activeDisguiseRegistry.getFakeName(online.getUniqueId()).ifPresent(options::add);
            });
            return TabCompletions.filterByPrefix(options, arguments[0]);
        }
        return List.of();
    }
}
