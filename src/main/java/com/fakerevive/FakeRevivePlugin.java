package com.fakerevive;

import com.fakerevive.command.FakeReviveCommand;
import com.fakerevive.disguise.ActiveDisguiseRegistry;
import com.fakerevive.disguise.FakeNamePool;
import com.fakerevive.disguise.MojangIdentityFetcher;
import com.fakerevive.disguise.PlayerDisguiseService;
import com.fakerevive.kit.KitManager;
import com.fakerevive.listener.FakeLeaveListener;
import com.fakerevive.message.MessageService;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.function.Supplier;

/**
 * Main plugin class for FakeRevive. Wires all components together on enable
 * and registers the single {@code /fr} command with its executor and tab completer.
 */
public class FakeRevivePlugin extends JavaPlugin {

    private static Supplier<MojangIdentityFetcher> identityFetcherFactory = MojangIdentityFetcher::new;

    private ActiveDisguiseRegistry activeDisguiseRegistry;
    private MessageService messageService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        String language = getConfig().getString("language", "en");
        this.messageService = new MessageService(this, language);

        activeDisguiseRegistry = new ActiveDisguiseRegistry();
        FakeNamePool fakeNamePool = new FakeNamePool(
                getResource("names.json"),
                new File(getDataFolder(), "used-fake-names.yml").toPath());
        PlayerDisguiseService playerDisguiseService = new PlayerDisguiseService(activeDisguiseRegistry);
        playerDisguiseService.registerPacketListener(this, messageService);
        MojangIdentityFetcher identityFetcher = identityFetcherFactory.get();
        KitManager kitManager = new KitManager(this);
        kitManager.loadKits();

        FakeLeaveListener fakeLeaveListener =
                new FakeLeaveListener(this, activeDisguiseRegistry, playerDisguiseService, messageService);
        getServer().getPluginManager().registerEvents(fakeLeaveListener, this);
        FakeReviveCommand fakeReviveCommand = new FakeReviveCommand(this, fakeLeaveListener, fakeNamePool,
                activeDisguiseRegistry, playerDisguiseService, identityFetcher, kitManager, getLogger(),
                messageService);
        getCommand("fr").setExecutor(fakeReviveCommand);
        getCommand("fr").setTabCompleter(fakeReviveCommand);
        getLogger().info(messageService.get("plugin.enable"));
    }

    @Override
    public void onDisable() {
        getLogger().info(messageService.get("plugin.disable"));
    }

    public ActiveDisguiseRegistry getActiveDisguiseRegistry() {
        return activeDisguiseRegistry;
    }

    /**
     * Lets tests substitute a stub that skips real Mojang network calls - MockBukkit executes
     * runTaskAsynchronously immediately on a real thread, so the default factory would otherwise
     * hit the live API on every revive during tests. Reset after each test.
     */
    public static void setIdentityFetcherFactoryForTesting(Supplier<MojangIdentityFetcher> factory) {
        identityFetcherFactory = factory;
    }
}
