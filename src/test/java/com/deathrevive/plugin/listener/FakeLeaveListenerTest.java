package com.deathrevive.plugin.listener;

import com.deathrevive.plugin.FakeLeaveAndRevivePlugin;
import com.deathrevive.plugin.disguise.FakeIdentity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeLeaveListenerTest {

    private ServerMock server;
    private FakeLeaveAndRevivePlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(FakeLeaveAndRevivePlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void deathMarksPlayerAsFakedOutBeforeTheRespawnTaskRuns() {
        PlayerMock player = server.addPlayer("Victim");

        player.damage(player.getHealth() + 1);

        PlayerQuitEvent quitEvent = new PlayerQuitEvent(player, Component.text("Victim left the game"));
        Bukkit.getPluginManager().callEvent(quitEvent);

        assertNull(quitEvent.quitMessage());
    }

    @Test
    void respawnTaskIsCancelledWhenPlayerDisconnectsBeforeItFires() {
        PlayerMock player = server.addPlayer("Victim");
        player.damage(player.getHealth() + 1);

        player.disconnect();

        assertDoesNotThrow(() -> server.getScheduler().performTicks(2L));
    }

    @Test
    void quitMessageIsUnaffectedForPlayerInSpectatorModeWithoutFakeLeave() {
        PlayerMock bystander = server.addPlayer("Bystander");
        bystander.setGameMode(GameMode.SPECTATOR);

        PlayerQuitEvent quitEvent = new PlayerQuitEvent(bystander, Component.text("Bystander left the game"));
        Bukkit.getPluginManager().callEvent(quitEvent);

        assertNotNull(quitEvent.quitMessage());
    }

    @Test
    void usesFakeNameInsteadOfRealNameInBroadcastWhileDisguiseIsActive() {
        PlayerMock target = server.addPlayer("Target");
        PlayerMock bystander = server.addPlayer("Bystander");
        plugin.getActiveDisguiseRegistry().assign(target.getUniqueId(),
                new FakeIdentity("Crimson_Wolf", "skin-value", "skin-signature"));
        drainMessages(bystander);

        target.damage(target.getHealth() + 1);

        assertEquals(
                Component.text("Crimson_Wolf got killed"),
                bystander.nextComponentMessage());
        assertEquals(
                Component.text("Crimson_Wolf left the game", NamedTextColor.YELLOW),
                bystander.nextComponentMessage());
        assertTrue(plugin.getActiveDisguiseRegistry().getFakeName(target.getUniqueId()).isEmpty());
    }

    private void drainMessages(PlayerMock player) {
        while (player.nextComponentMessage() != null) {
        }
    }

    @Test
    @Disabled("TODO: MockBukkit's PlayerSpigotMock does not implement respawn() (throws "
            + "UnsupportedOperationException), so the delayed respawn task cannot be ticked "
            + "through in a test without hitting that unimplemented method. Re-enable once "
            + "MockBukkit provides a working respawn() mock.")
    void playerEndsUpInSpectatorAtDeathLocationAfterTheRespawnTask() {
        PlayerMock player = server.addPlayer("Victim");
        player.damage(player.getHealth() + 1);

        server.getScheduler().performTicks(2L);

        assertEquals(GameMode.SPECTATOR, player.getGameMode());
    }
}
