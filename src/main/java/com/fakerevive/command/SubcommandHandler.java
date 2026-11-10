package com.fakerevive.command;

import org.bukkit.command.CommandSender;

import java.util.List;

public interface SubcommandHandler {
    String name();

    String permission();

    boolean execute(CommandSender sender, String[] arguments);

    default List<String> tabComplete(CommandSender sender, String[] arguments) {
        return List.of();
    }
}
