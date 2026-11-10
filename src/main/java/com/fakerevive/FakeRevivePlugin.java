package com.fakerevive;

import com.fakerevive.armor.ArmorLockListener;
import com.fakerevive.armor.ArmorLockManager;
import com.fakerevive.command.ArmorCommandHandler;
import com.fakerevive.command.DisguiseCommandHandler;
import com.fakerevive.command.ExemptCommandHandler;
import com.fakerevive.command.FakeReviveCommand;
import com.fakerevive.command.HelpCommandHandler;
import com.fakerevive.command.KitCommandHandler;
import com.fakerevive.command.LeaveCommandHandler;
import com.fakerevive.command.PoolCommandHandler;
import com.fakerevive.command.ReloadCommandHandler;
import com.fakerevive.command.ReviveCommandHandler;
import com.fakerevive.command.SubcommandHandler;
import com.fakerevive.command.TargetResolver;
import com.fakerevive.command.TeamCommandHandler;
import com.fakerevive.command.UndisguiseCommandHandler;
import com.fakerevive.disguise.ActiveDisguiseRegistry;
import com.fakerevive.disguise.DisguiseCoordinator;
import com.fakerevive.disguise.FakeNamePool;
import com.fakerevive.disguise.FakeNamePoolReplenisher;
import com.fakerevive.disguise.MojangIdentityFetcher;
import com.fakerevive.disguise.PlayerDisguiseService;
import com.fakerevive.disguise.ReplenishSettings;
import com.fakerevive.exempt.ExemptManager;
import com.fakerevive.kit.KitManager;
import com.fakerevive.listener.FakeLeaveListener;
import com.fakerevive.message.MessageService;
import com.fakerevive.storage.FakedOutPlayerStore;
import com.fakerevive.team.TeamGui;
import com.fakerevive.team.TeamGuiListener;
import com.fakerevive.team.TeamManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

public class FakeRevivePlugin extends JavaPlugin {
    private ActiveDisguiseRegistry activeDisguiseRegistry;
    private MessageService messageService;
    private FakeNamePool fakeNamePool;
    private PlayerDisguiseService playerDisguiseService;
    private MojangIdentityFetcher identityFetcher;
    private FakeNamePoolReplenisher fakeNamePoolReplenisher;
    private KitManager kitManager;
    private ArmorLockManager armorLockManager;
    private ExemptManager exemptManager;
    private TeamManager teamManager;
    private TeamGui teamGui;
    private FakedOutPlayerStore fakedOutPlayerStore;

    @Override
    public void onEnable() {
        loadMessages();
        buildDisguise();
        loadKits();
        loadArmorLocks();
        loadExemptions();
        loadTeams();
        loadFakedOutPlayers();
        registerCommand();
        startNamePool();
    }

    @Override
    public void onDisable() {
        if (fakeNamePoolReplenisher != null) {
            fakeNamePoolReplenisher.stop();
        }
        getLogger().info(messageService.get("plugin.disable"));
    }

    public ActiveDisguiseRegistry getActiveDisguiseRegistry() {
        return activeDisguiseRegistry;
    }

    public FakeNamePool getFakeNamePool() {
        return fakeNamePool;
    }

    protected MojangIdentityFetcher createIdentityFetcher() {
        return new MojangIdentityFetcher();
    }

    private void loadMessages() {
        saveDefaultConfig();
        String language = getConfig().getString("language", "en");
        this.messageService = new MessageService(this, language);
    }

    private void buildDisguise() {
        activeDisguiseRegistry = new ActiveDisguiseRegistry();
        fakeNamePool = new FakeNamePool(
                getResource("names.json"),
                new File(getDataFolder(), "used-fake-names.yml").toPath(),
                getLogger());
        playerDisguiseService = new PlayerDisguiseService(activeDisguiseRegistry);
        playerDisguiseService.registerPacketListener(this, messageService);
        identityFetcher = createIdentityFetcher();
        fakeNamePoolReplenisher = new FakeNamePoolReplenisher(this, fakeNamePool, identityFetcher,
                getLogger(), messageService, ReplenishSettings.fromConfig(getConfig()));
    }

    private void loadKits() {
        kitManager = new KitManager(this);
        kitManager.loadKits();
    }

    private void loadArmorLocks() {
        armorLockManager = new ArmorLockManager(this);
        armorLockManager.loadLocks();
        getServer().getPluginManager().registerEvents(
                new ArmorLockListener(this, armorLockManager, messageService), this);
    }

    private void loadExemptions() {
        exemptManager = new ExemptManager(this);
        exemptManager.load();
    }

    private void loadTeams() {
        teamManager = new TeamManager(this);
        teamManager.loadTeams();
        teamGui = new TeamGui(this, teamManager, kitManager, messageService);
        TeamGuiListener teamGuiListener = new TeamGuiListener(this, teamManager, teamGui, kitManager, messageService);
        getServer().getPluginManager().registerEvents(teamGuiListener, this);
    }

    private void loadFakedOutPlayers() {
        fakedOutPlayerStore = new FakedOutPlayerStore(
                new File(getDataFolder(), "faked-out.yml").toPath(), getLogger());
        fakedOutPlayerStore.load();
        FakeLeaveListener fakeLeaveListener = new FakeLeaveListener(
                this, activeDisguiseRegistry, playerDisguiseService, fakedOutPlayerStore, messageService);
        getServer().getPluginManager().registerEvents(fakeLeaveListener, this);
    }

    private void registerCommand() {
        TargetResolver targetResolver = new TargetResolver(teamManager, activeDisguiseRegistry);
        DisguiseCoordinator disguiseCoordinator = new DisguiseCoordinator(this, fakeNamePool,
                fakeNamePoolReplenisher, activeDisguiseRegistry, playerDisguiseService, identityFetcher, kitManager,
                messageService, getLogger());
        List<SubcommandHandler> handlers = List.of(
                new ReviveCommandHandler(messageService, fakedOutPlayerStore, teamManager, kitManager,
                        disguiseCoordinator),
                new UndisguiseCommandHandler(messageService, activeDisguiseRegistry, disguiseCoordinator),
                new DisguiseCommandHandler(messageService, fakedOutPlayerStore, exemptManager, disguiseCoordinator),
                new ExemptCommandHandler(messageService, exemptManager),
                new KitCommandHandler(this, messageService, kitManager, teamManager, targetResolver),
                new TeamCommandHandler(messageService, teamGui),
                new ArmorCommandHandler(messageService, armorLockManager, targetResolver),
                new PoolCommandHandler(messageService, fakeNamePool, fakeNamePoolReplenisher),
                new LeaveCommandHandler(messageService, teamManager),
                new ReloadCommandHandler(this, messageService, kitManager, teamManager, armorLockManager,
                        exemptManager, fakedOutPlayerStore, fakeNamePoolReplenisher),
                new HelpCommandHandler(messageService));
        FakeReviveCommand fakeReviveCommand = new FakeReviveCommand(messageService, handlers);
        getCommand("fr").setExecutor(fakeReviveCommand);
        getCommand("fr").setTabCompleter(fakeReviveCommand);
    }

    private void startNamePool() {
        fakeNamePoolReplenisher.start();
        if (fakeNamePool.availableCount() == 0) {
            getLogger().warning(messageService.get("events.pool-low",
                    "available", "0", "target", String.valueOf(fakeNamePoolReplenisher.getTargetSize())));
        }
        getLogger().info(messageService.get("plugin.enable"));
    }
}
