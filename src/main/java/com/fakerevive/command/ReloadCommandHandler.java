package com.fakerevive.command;

import com.fakerevive.armor.ArmorLockManager;
import com.fakerevive.disguise.FakeNamePoolReplenisher;
import com.fakerevive.disguise.ReplenishSettings;
import com.fakerevive.exempt.ExemptManager;
import com.fakerevive.kit.KitManager;
import com.fakerevive.message.MessageService;
import com.fakerevive.storage.FakedOutPlayerStore;
import com.fakerevive.team.TeamManager;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class ReloadCommandHandler implements SubcommandHandler {
    private final JavaPlugin plugin;
    private final MessageService messageService;
    private final KitManager kitManager;
    private final TeamManager teamManager;
    private final ArmorLockManager armorLockManager;
    private final ExemptManager exemptManager;
    private final FakedOutPlayerStore fakedOutPlayerStore;
    private final FakeNamePoolReplenisher replenisher;

    public ReloadCommandHandler(JavaPlugin plugin, MessageService messageService, KitManager kitManager,
                                TeamManager teamManager, ArmorLockManager armorLockManager,
                                ExemptManager exemptManager, FakedOutPlayerStore fakedOutPlayerStore,
                                FakeNamePoolReplenisher replenisher) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.kitManager = kitManager;
        this.teamManager = teamManager;
        this.armorLockManager = armorLockManager;
        this.exemptManager = exemptManager;
        this.fakedOutPlayerStore = fakedOutPlayerStore;
        this.replenisher = replenisher;
    }

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public String permission() {
        return CommandPermissions.RELOAD;
    }

    @Override
    public boolean execute(CommandSender sender, String[] arguments) {
        plugin.reloadConfig();
        messageService.reload(plugin);
        kitManager.loadKits();
        teamManager.reload();
        armorLockManager.loadLocks();
        exemptManager.load();
        fakedOutPlayerStore.load();
        replenisher.applySettings(ReplenishSettings.fromConfig(plugin.getConfig()));
        messageService.send(sender, "commands.reload.success", NamedTextColor.GREEN);
        return true;
    }
}
