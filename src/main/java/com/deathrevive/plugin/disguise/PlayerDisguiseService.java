package com.deathrevive.plugin.disguise;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.PlayerDisguise;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PlayerDisguiseService {

    public void apply(Player player, String fakeName) {
        if (!isLibsDisguisesAvailable()) {
            return;
        }

        PlayerDisguise disguise = new PlayerDisguise(fakeName);
        disguise.setName(fakeName);
        disguise.setTablistName(fakeName);
        disguise.setDisplayedInTab(true);
        disguise.setReplaceSounds(true);
        disguise.setKeepDisguiseOnPlayerDeath(true);
        DisguiseAPI.disguiseToAll(player, disguise);
    }

    public void remove(Player player) {
        if (!isLibsDisguisesAvailable()) {
            return;
        }

        if (DisguiseAPI.isDisguised(player)) {
            DisguiseAPI.undisguiseToAll(player);
        }
    }

    private boolean isLibsDisguisesAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("LibsDisguises");
    }
}
