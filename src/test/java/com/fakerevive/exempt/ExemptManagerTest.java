package com.fakerevive.exempt;

import com.fakerevive.FakeRevivePlugin;
import com.fakerevive.disguise.FakeIdentity;
import com.fakerevive.disguise.MojangIdentityFetcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExemptManagerTest {

    private ServerMock server;
    private FakeRevivePlugin plugin;
    private ExemptManager exemptManager;

    @BeforeEach
    void setUp() {
        FakeRevivePlugin.setIdentityFetcherFactoryForTesting(() -> new MojangIdentityFetcher() {
            @Override
            public Optional<FakeIdentity> fetchNewIdentity(Set<String> excludedNames, int maxAttempts) {
                return Optional.empty();
            }
        });
        server = MockBukkit.mock();
        plugin = MockBukkit.load(FakeRevivePlugin.class);
        exemptManager = new ExemptManager(plugin);
        exemptManager.load();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
        FakeRevivePlugin.setIdentityFetcherFactoryForTesting(MojangIdentityFetcher::new);
    }

    @Test
    void addsAndRemovesStoredPlayers() {
        PlayerMock player = server.addPlayer("Stored");

        assertFalse(exemptManager.isExempt(player));
        assertTrue(exemptManager.add(player.getUniqueId(), player.getName()));
        assertFalse(exemptManager.add(player.getUniqueId(), player.getName()));
        assertTrue(exemptManager.isStored(player.getUniqueId()));
        assertTrue(exemptManager.isExempt(player));
        assertEquals(Optional.of(player.getUniqueId()), exemptManager.findStoredByName("stored"));

        assertTrue(exemptManager.remove(player.getUniqueId()));
        assertFalse(exemptManager.remove(player.getUniqueId()));
        assertFalse(exemptManager.isStored(player.getUniqueId()));
        assertFalse(exemptManager.isExempt(player));
    }

    @Test
    void savesStoredPlayersAndReloadsThemFromTheFile() {
        PlayerMock player = server.addPlayer("Persisted");
        exemptManager.add(player.getUniqueId(), player.getName());

        File exemptFile = new File(plugin.getDataFolder(), "exempt-players.yml");
        assertTrue(exemptFile.exists());

        ExemptManager reloadedManager = new ExemptManager(plugin);
        reloadedManager.load();

        assertEquals(Map.of(player.getUniqueId(), "Persisted"), reloadedManager.getStoredPlayers());
        assertTrue(reloadedManager.isExempt(player));
    }

    @Test
    void loadReplacesPreviousEntriesWithTheFileContent() {
        PlayerMock player = server.addPlayer("Removed");
        exemptManager.add(player.getUniqueId(), player.getName());
        ExemptManager otherManager = new ExemptManager(plugin);
        otherManager.load();
        otherManager.remove(player.getUniqueId());

        exemptManager.load();

        assertFalse(exemptManager.isStored(player.getUniqueId()));
        assertTrue(exemptManager.getStoredPlayers().isEmpty());
    }

    @Test
    void configuredNamesIgnoreCase() {
        PlayerMock player = server.addPlayer("ConfigPlayer");
        plugin.getConfig().set("exempt-players", List.of("configplayer"));

        assertFalse(exemptManager.isExempt(player));

        exemptManager.load();

        assertTrue(exemptManager.isConfigured("CONFIGPLAYER"));
        assertTrue(exemptManager.isExempt(player));
        assertEquals(List.of("configplayer"), exemptManager.getConfiguredNames());
        assertFalse(exemptManager.isStored(player.getUniqueId()));
    }

    @Test
    void reloadingTheConfigDropsRemovedNames() {
        PlayerMock player = server.addPlayer("ConfigPlayer");
        plugin.getConfig().set("exempt-players", List.of("ConfigPlayer"));
        exemptManager.load();
        assertTrue(exemptManager.isExempt(player));

        plugin.getConfig().set("exempt-players", List.of());
        exemptManager.load();

        assertFalse(exemptManager.isExempt(player));
        assertTrue(exemptManager.getConfiguredNames().isEmpty());
    }

    @Test
    void exemptPermissionMakesPlayerExempt() {
        PlayerMock player = server.addPlayer("PermissionPlayer");

        player.addAttachment(plugin, ExemptManager.PERMISSION_EXEMPT, true);

        assertTrue(exemptManager.isExempt(player));
    }

    @Test
    void operatorsAreNotExemptByDefault() {
        PlayerMock operator = server.addPlayer("Operator");
        operator.setOp(true);

        assertTrue(operator.hasPermission("fakerevive.admin"));
        assertFalse(operator.hasPermission(ExemptManager.PERMISSION_EXEMPT));
        assertFalse(exemptManager.isExempt(operator));
    }
}
