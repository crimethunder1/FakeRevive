package com.deathrevive.plugin.disguise;

import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.PlayerDisguise;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class PlayerDisguiseService {

    public void apply(Player player, FakeIdentity identity) {
        if (!isLibsDisguisesAvailable()) {
            return;
        }

        PlayerDisguise disguise = new PlayerDisguise(identity.name());
        disguise.setName(identity.name());
        disguise.setTablistName(identity.name());
        disguise.setDisplayedInTab(true);
        disguise.setReplaceSounds(true);
        disguise.setKeepDisguiseOnPlayerDeath(true);

        UserProfile skinProfile = new UserProfile(UUID.randomUUID(), identity.name(),
                List.of(new TextureProperty("textures", identity.skinValue(), identity.skinSignature())));
        disguise.setSkin(skinProfile);

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
