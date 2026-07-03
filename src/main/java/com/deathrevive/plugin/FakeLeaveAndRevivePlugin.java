package com.deathrevive.plugin;

import com.deathrevive.plugin.command.ReviveCommand;
import com.deathrevive.plugin.listener.FakeLeaveListener;
import org.bukkit.plugin.java.JavaPlugin;

public class FakeLeaveAndRevivePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        FakeLeaveListener fakeLeaveListener = new FakeLeaveListener(this);
        getServer().getPluginManager().registerEvents(fakeLeaveListener, this);
        this.getCommand("revive").setExecutor(new ReviveCommand(fakeLeaveListener));
        getLogger().info("Fake-Leave & Revive (Paper-Native) erfolgreich aktiviert!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Fake-Leave & Revive Plugin deactivated.");
    }
}
