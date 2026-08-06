package com.fakerevive.disguise;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.fakerevive.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Applies and removes player disguises without spawning extra entities. Uses PacketEvents
 * to intercept outgoing {@code PLAYER_INFO_UPDATE ADD_PLAYER} packets and rewrite name and
 * skin before they reach each client. A hide/show cycle on each observer forces the client
 * to despawn and respawn the player entity, picking up the freshly injected profile.
 * <p>Requires PacketEvents to be installed as a server plugin. If unavailable,
 * {@link #apply} and {@link #remove} are no-ops and a warning is logged on startup.
 */
public class PlayerDisguiseService {

    private final ActiveDisguiseRegistry activeDisguiseRegistry;
    private JavaPlugin plugin;

    public PlayerDisguiseService(ActiveDisguiseRegistry activeDisguiseRegistry) {
        this.activeDisguiseRegistry = activeDisguiseRegistry;
    }

    /**
     * Registers the packet interceptor with PacketEvents. Must be called once during plugin
     * enable, before any {@link #apply} or {@link #remove} calls.
     */
    public void registerPacketListener(JavaPlugin owningPlugin, MessageService messageService) {
        this.plugin = owningPlugin;
        if (!isPacketEventsAvailable()) {
            owningPlugin.getLogger().warning(messageService.get("plugin.packetevents-missing"));
            return;
        }
        PacketEvents.getAPI().getEventManager().registerListeners(new PlayerInfoInterceptor(activeDisguiseRegistry));
    }

    /**
     * Pushes the fake profile to every currently online observer and triggers an entity
     * re-render so they see the new name and skin immediately. Must be called after the
     * identity has been stored in {@link ActiveDisguiseRegistry}.
     */
    public void apply(Player player, FakeIdentity identity) {
        if (plugin == null || !isPacketEventsAvailable()) return;
        UserProfile fakeProfile = new UserProfile(player.getUniqueId(), identity.name(),
                List.of(new TextureProperty("textures", identity.skinValue(), identity.skinSignature())));
        refreshEntityForAllObservers(player, fakeProfile);
    }

    /**
     * Restores the real profile for every currently online observer. Must be called after
     * the entry has been cleared from {@link ActiveDisguiseRegistry} so the interceptor
     * no longer rewrites subsequent outgoing packets for this player.
     */
    public void remove(Player player) {
        if (plugin == null || !isPacketEventsAvailable()) return;
        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) return;
        UserProfile realProfile = user.getProfile();
        refreshEntityForAllObservers(player, realProfile);
    }

    /**
     * Re-sends the given profile to every observer. Operators additionally get an
     * {@code UPDATE_DISPLAY_NAME} entry showing the real name in front of the fake one, so staff
     * can still tell who is behind a disguise in the tab list.
     */
    private void refreshEntityForAllObservers(Player player, UserProfile profile) {
        UUID playerId = player.getUniqueId();
        String realName = player.getName();
        boolean isDisguised = activeDisguiseRegistry.getIdentity(playerId).isPresent();

        WrapperPlayServerPlayerInfoRemove removePacket = new WrapperPlayServerPlayerInfoRemove(List.of(playerId));

        for (Player observer : Bukkit.getOnlinePlayers()) {
            if (observer.getUniqueId().equals(playerId)) continue;

            PacketEvents.getAPI().getPlayerManager().sendPacket(observer, removePacket);

            WrapperPlayServerPlayerInfoUpdate.PlayerInfo entry =
                    new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(profile);
            EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> actions =
                    EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER);

            if (isDisguised && observer.isOp()) {
                entry.setDisplayName(Component.text("[" + realName + "] ", NamedTextColor.AQUA)
                        .append(Component.text(profile.getName(), NamedTextColor.WHITE)));
                actions.add(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME);
            }

            PacketEvents.getAPI().getPlayerManager().sendPacket(observer,
                    new WrapperPlayServerPlayerInfoUpdate(actions, List.of(entry)));

            observer.hidePlayer(plugin, player);
            observer.showPlayer(plugin, player);
        }
    }

    private boolean isPacketEventsAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("packetevents");
    }

    private static class PlayerInfoInterceptor extends PacketListenerAbstract {

        private final ActiveDisguiseRegistry registry;

        PlayerInfoInterceptor(ActiveDisguiseRegistry registry) {
            this.registry = registry;
        }

        @Override
        public void onPacketSend(PacketSendEvent event) {
            if (event.getPacketType() != PacketType.Play.Server.PLAYER_INFO_UPDATE) return;

            WrapperPlayServerPlayerInfoUpdate wrapper = new WrapperPlayServerPlayerInfoUpdate(event);
            if (!wrapper.getActions().contains(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER)) return;

            Player recipient = (Player) event.getPlayer();
            if (recipient == null) return;

            boolean modified = false;
            boolean displayNamesSet = false;
            for (WrapperPlayServerPlayerInfoUpdate.PlayerInfo entry : wrapper.getEntries()) {
                UUID entryId = entry.getProfileId();

                if (entryId.equals(recipient.getUniqueId())) continue;

                Optional<FakeIdentity> identity = registry.getIdentity(entryId);
                if (identity.isEmpty()) continue;

                FakeIdentity fakeIdentity = identity.get();
                UserProfile fakeProfile = new UserProfile(entryId, fakeIdentity.name(),
                        List.of(new TextureProperty("textures", fakeIdentity.skinValue(), fakeIdentity.skinSignature())));
                entry.setGameProfile(fakeProfile);

                if (recipient.isOp()) {
                    Player realPlayer = Bukkit.getPlayer(entryId);
                    if (realPlayer != null) {
                        entry.setDisplayName(Component.text("[" + realPlayer.getName() + "] ", NamedTextColor.AQUA)
                                .append(Component.text(fakeIdentity.name(), NamedTextColor.WHITE)));
                        displayNamesSet = true;
                    }
                }

                modified = true;
            }

            if (displayNamesSet && !wrapper.getActions().contains(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME)) {
                EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> actions = EnumSet.copyOf(wrapper.getActions());
                actions.add(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME);
                wrapper.setActions(actions);
            }

            if (modified) {
                event.markForReEncode(true);
            }
        }
    }
}
