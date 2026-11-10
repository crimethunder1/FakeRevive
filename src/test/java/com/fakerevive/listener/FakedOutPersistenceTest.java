package com.fakerevive.listener;

import com.fakerevive.FakeRevivePlugin;
import com.fakerevive.support.PluginLoader;
import com.fakerevive.disguise.FakeIdentity;
import com.fakerevive.disguise.MojangIdentityFetcher;
import com.fakerevive.storage.FakedOutPlayerStore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakedOutPersistenceTest {
    private ServerMock server;
    private FakeRevivePlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = PluginLoader.load();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private File fakedOutFile() {
        return new File(plugin.getDataFolder(), "faked-out.yml");
    }

    private FakedOutPlayerStore freshlyLoadedStore() {
        FakedOutPlayerStore store = new FakedOutPlayerStore(fakedOutFile().toPath(), plugin.getLogger());
        store.load();
        return store;
    }

    private PlayerJoinEvent callJoin(PlayerMock player) {
        PlayerJoinEvent joinEvent = new PlayerJoinEvent(player, Component.text(player.getName() + " joined the game"));
        Bukkit.getPluginManager().callEvent(joinEvent);
        return joinEvent;
    }

    @Test
    void deathIsWrittenToTheFileAndSurvivesALoadIntoANewStore() {
        PlayerMock player = server.addPlayer("Victim");

        player.damage(player.getHealth() + 1);

        assertTrue(fakedOutFile().exists());
        assertTrue(freshlyLoadedStore().contains(player.getUniqueId()));
    }

    @Test
    void reviveRemovesTheEntryPermanently() {
        PlayerMock player = server.addPlayer("Victim");
        player.damage(player.getHealth() + 1);
        assertTrue(freshlyLoadedStore().contains(player.getUniqueId()));

        server.dispatchCommand(server.getConsoleSender(), "fr revive Victim");

        assertFalse(freshlyLoadedStore().contains(player.getUniqueId()));
    }

    @Test
    void quitWithPendingRespawnTaskRemovesTheEntryFromTheFile() {
        PlayerMock player = server.addPlayer("Victim");
        player.damage(player.getHealth() + 1);

        Bukkit.getPluginManager().callEvent(new PlayerQuitEvent(player,
                Component.text("Victim left the game"), PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertFalse(freshlyLoadedStore().contains(player.getUniqueId()));
    }

    @Test
    void joinMessageIsSuppressedForFakedOutPlayerInSpectatorMode() {
        PlayerMock player = server.addPlayer("Victim");
        player.damage(player.getHealth() + 1);
        player.setGameMode(GameMode.SPECTATOR);

        PlayerJoinEvent joinEvent = callJoin(player);

        assertNull(joinEvent.joinMessage());
        assertTrue(freshlyLoadedStore().contains(player.getUniqueId()));
    }

    @Test
    void entryWithoutSpectatorModeIsRemovedAndJoinMessageStays() {
        PlayerMock player = server.addPlayer("Victim");
        player.damage(player.getHealth() + 1);
        player.setGameMode(GameMode.SURVIVAL);

        PlayerJoinEvent joinEvent = callJoin(player);

        assertNotNull(joinEvent.joinMessage());
        assertFalse(freshlyLoadedStore().contains(player.getUniqueId()));
    }

    @Test
    void joinMessageIsKeptForPlayerThatIsNotFakedOut() {
        PlayerMock player = server.addPlayer("Bystander");
        player.setGameMode(GameMode.SPECTATOR);

        PlayerJoinEvent joinEvent = callJoin(player);

        assertNotNull(joinEvent.joinMessage());
    }

    @Test
    void reloadCommandReadsEntriesWrittenToTheFile() throws IOException {
        PlayerMock player = server.addPlayer("Victim");
        player.setGameMode(GameMode.SPECTATOR);
        UUID playerId = player.getUniqueId();
        Files.writeString(fakedOutFile().toPath(), "players:\n  - " + playerId + "\n");

        server.dispatchCommand(server.getConsoleSender(), "fr reload");

        assertNull(callJoin(player).joinMessage());
    }

    @Test
    void brokenFileIsBackedUpOnReload() throws IOException {
        Files.writeString(fakedOutFile().toPath(), "players: [unclosed\n  - : :\n");

        server.dispatchCommand(server.getConsoleSender(), "fr reload");

        assertTrue(new File(plugin.getDataFolder(), "faked-out.yml.broken").exists());
        assertEquals("players: [unclosed\n  - : :\n",
                Files.readString(new File(plugin.getDataFolder(), "faked-out.yml.broken").toPath()));
    }

    @Test
    @Disabled("TODO: Needs a real server restart with the respawn task and spectator mode. MockBukkit's "
            + "PlayerSpigotMock does not implement respawn(), so the full cycle of death, restart, join "
            + "and revive cannot be run end to end in a test.")
    void deadPlayerCanBeRevivedAfterTheServerRestarted() {
    }
}
