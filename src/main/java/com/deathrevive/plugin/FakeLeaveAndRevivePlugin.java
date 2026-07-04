package com.deathrevive.plugin;

import com.deathrevive.plugin.command.ReviveCommand;
import com.deathrevive.plugin.disguise.ActiveDisguiseRegistry;
import com.deathrevive.plugin.disguise.FakeNamePool;
import com.deathrevive.plugin.disguise.PlayerDisguiseService;
import com.deathrevive.plugin.listener.FakeLeaveListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class FakeLeaveAndRevivePlugin extends JavaPlugin {

    private ActiveDisguiseRegistry activeDisguiseRegistry;

    @Override
    public void onEnable() {
        activeDisguiseRegistry = new ActiveDisguiseRegistry();
        FakeNamePool fakeNamePool = new FakeNamePool(
                getResource("names.json"),
                new File(getDataFolder(), "used-fake-names.yml").toPath());
        PlayerDisguiseService playerDisguiseService = new PlayerDisguiseService();

        FakeLeaveListener fakeLeaveListener =
                new FakeLeaveListener(this, activeDisguiseRegistry, playerDisguiseService);
        getServer().getPluginManager().registerEvents(fakeLeaveListener, this);
        this.getCommand("revive").setExecutor(new ReviveCommand(
                fakeLeaveListener, fakeNamePool, activeDisguiseRegistry, playerDisguiseService, getLogger()));
        getLogger().info("Fake-Leave & Revive (Paper-Native) erfolgreich aktiviert!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Fake-Leave & Revive Plugin deactivated.");
    }

    public ActiveDisguiseRegistry getActiveDisguiseRegistry() {
        return activeDisguiseRegistry;
    }
}
