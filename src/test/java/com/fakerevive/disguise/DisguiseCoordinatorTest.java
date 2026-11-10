package com.fakerevive.disguise;

import com.fakerevive.FakeRevivePlugin;
import com.fakerevive.kit.KitManager;
import com.fakerevive.message.MessageService;
import com.fakerevive.support.PluginLoader;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisguiseCoordinatorTest {
    private static final FakeIdentity OLD_IDENTITY = new FakeIdentity("OldName", "oldValue", "oldSignature");

    @TempDir
    Path temporaryDirectory;

    private ServerMock server;
    private PlayerMock player;
    private ActiveDisguiseRegistry registry;
    private RecordingDisguiseService disguiseService;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Target");
        registry = new ActiveDisguiseRegistry();
        disguiseService = new RecordingDisguiseService(registry);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void keepsTheOldDisguiseWhenThePoolIsEmpty() {
        DisguiseCoordinator coordinator = createCoordinator("[]");
        registry.assign(player.getUniqueId(), OLD_IDENTITY);

        Optional<FakeIdentity> result = coordinator.assignFromPool(player);

        assertTrue(result.isEmpty());
        assertEquals(Optional.of(OLD_IDENTITY), registry.getIdentity(player.getUniqueId()));
        assertTrue(disguiseService.appliedPlayers.isEmpty());
        assertTrue(disguiseService.removedPlayers.isEmpty());
    }

    @Test
    void replacesTheOldDisguiseOnceANewIdentityIsAvailable() {
        DisguiseCoordinator coordinator = createCoordinator(
                "[{\"name\":\"NewName\",\"skinValue\":\"newValue\",\"skinSignature\":\"newSignature\"}]");
        registry.assign(player.getUniqueId(), OLD_IDENTITY);

        Optional<FakeIdentity> result = coordinator.assignFromPool(player);

        assertEquals("NewName", result.orElseThrow().name());
        assertEquals(result, registry.getIdentity(player.getUniqueId()));
        assertEquals(List.of(player), disguiseService.appliedPlayers);
    }

    @Test
    void removeClearsTheRegistryAndCallsTheService() {
        DisguiseCoordinator coordinator = createCoordinator("[]");
        registry.assign(player.getUniqueId(), OLD_IDENTITY);

        coordinator.remove(player);

        assertTrue(registry.getIdentity(player.getUniqueId()).isEmpty());
        assertEquals(List.of(player), disguiseService.removedPlayers);
    }

    private DisguiseCoordinator createCoordinator(String namesJson) {
        FakeRevivePlugin plugin = PluginLoader.load();
        Logger logger = plugin.getLogger();
        FakeNamePool pool = new FakeNamePool(
                new ByteArrayInputStream(namesJson.getBytes(StandardCharsets.UTF_8)),
                temporaryDirectory.resolve("used-fake-names.yml"), logger);
        MojangIdentityFetcher fetcher = new MojangIdentityFetcher();
        MessageService messageService = new MessageService(plugin, "en");
        ReplenishSettings disabledSettings = new ReplenishSettings(false, 1, 0, 1, 600L,
                Duration.ofMillis(200L), 1, false);
        FakeNamePoolReplenisher replenisher = new FakeNamePoolReplenisher(plugin, pool, fetcher, logger,
                messageService, disabledSettings);
        return new DisguiseCoordinator(plugin, pool, replenisher, registry, disguiseService, fetcher,
                new KitManager(plugin), messageService, logger);
    }

    private static final class RecordingDisguiseService extends PlayerDisguiseService {
        private final List<Player> appliedPlayers = new ArrayList<>();
        private final List<Player> removedPlayers = new ArrayList<>();

        private RecordingDisguiseService(ActiveDisguiseRegistry registry) {
            super(registry);
        }

        @Override
        public void apply(Player target, FakeIdentity identity) {
            appliedPlayers.add(target);
        }

        @Override
        public void remove(Player target) {
            removedPlayers.add(target);
        }
    }
}
