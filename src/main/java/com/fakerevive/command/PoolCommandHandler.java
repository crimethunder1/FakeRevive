package com.fakerevive.command;

import com.fakerevive.disguise.FakeNamePool;
import com.fakerevive.disguise.FakeNamePoolReplenisher;
import com.fakerevive.message.MessageService;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.List;

public class PoolCommandHandler implements SubcommandHandler {
    private final MessageService messageService;
    private final FakeNamePool fakeNamePool;
    private final FakeNamePoolReplenisher replenisher;

    public PoolCommandHandler(MessageService messageService, FakeNamePool fakeNamePool,
                              FakeNamePoolReplenisher replenisher) {
        this.messageService = messageService;
        this.fakeNamePool = fakeNamePool;
        this.replenisher = replenisher;
    }

    @Override
    public String name() {
        return "pool";
    }

    @Override
    public String permission() {
        return CommandPermissions.POOL;
    }

    @Override
    public boolean execute(CommandSender sender, String[] arguments) {
        if (arguments.length == 0) {
            messageService.send(sender, "commands.pool.status", NamedTextColor.YELLOW, "available",
                    String.valueOf(fakeNamePool.availableCount()), "used", String.valueOf(fakeNamePool.usedCount()),
                    "target", String.valueOf(replenisher.getTargetSize()));
            messageService.send(sender, replenisher.isRefilling() ? "commands.pool.refilling" : "commands.pool.idle",
                    NamedTextColor.GRAY);
            return true;
        }

        if (arguments[0].equalsIgnoreCase("refill")) {
            switch (replenisher.requestManualRefill()) {
                case STARTED -> messageService.send(sender, "commands.pool.refill-started", NamedTextColor.GREEN);
                case ALREADY_RUNNING ->
                        messageService.send(sender, "commands.pool.refill-running", NamedTextColor.GRAY);
                case POOL_FULL -> messageService.send(sender, "commands.pool.refill-full", NamedTextColor.YELLOW,
                        "target", String.valueOf(replenisher.getTargetSize()));
            }
            return true;
        }

        messageService.send(sender, "commands.pool.usage", NamedTextColor.RED);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] arguments) {
        return arguments.length == 1 ? TabCompletions.filterByPrefix(List.of("refill"), arguments[0]) : List.of();
    }
}
