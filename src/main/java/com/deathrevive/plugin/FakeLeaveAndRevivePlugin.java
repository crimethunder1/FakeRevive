package com.deathrevive.plugin;

import com.deathrevive.plugin.command.KitCommand;
import com.deathrevive.plugin.command.ReviveCommand;
import com.deathrevive.plugin.command.UndisguiseCommand;
import com.deathrevive.plugin.disguise.ActiveDisguiseRegistry;
import com.deathrevive.plugin.disguise.FakeNamePool;
import com.deathrevive.plugin.disguise.MojangIdentityFetcher;
import com.deathrevive.plugin.disguise.PlayerDisguiseService;
import com.deathrevive.plugin.kit.KitManager;
import com.deathrevive.plugin.listener.FakeLeaveListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.function.Supplier;

public class FakeLeaveAndRevivePlugin extends JavaPlugin {

    private static Supplier<MojangIdentityFetcher> identityFetcherFactory = MojangIdentityFetcher::new;

    private ActiveDisguiseRegistry activeDisguiseRegistry;

    @Override
    public void onEnable() {
        activeDisguiseRegistry = new ActiveDisguiseRegistry();
        FakeNamePool fakeNamePool = new FakeNamePool(
                getResource("names.json"),
                new File(getDataFolder(), "used-fake-names.yml").toPath());
        PlayerDisguiseService playerDisguiseService = new PlayerDisguiseService(activeDisguiseRegistry);
        playerDisguiseService.registerPacketListener(this);
        MojangIdentityFetcher identityFetcher = identityFetcherFactory.get();
        KitManager kitManager = new KitManager(this);
        kitManager.loadKits();

        FakeLeaveListener fakeLeaveListener =
                new FakeLeaveListener(this, activeDisguiseRegistry, playerDisguiseService);
        getServer().getPluginManager().registerEvents(fakeLeaveListener, this);
        this.getCommand("revive").setExecutor(new ReviveCommand(this, fakeLeaveListener, fakeNamePool,
                activeDisguiseRegistry, playerDisguiseService, identityFetcher, kitManager, getLogger()));
        this.getCommand("undisguise").setExecutor(
                new UndisguiseCommand(activeDisguiseRegistry, playerDisguiseService));
        this.getCommand("kit").setExecutor(new KitCommand(kitManager));
        getLogger().info("Fake-Leave & Revive (PacketEvents-Native) erfolgreich aktiviert!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Fake-Leave & Revive Plugin deactivated.");
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
