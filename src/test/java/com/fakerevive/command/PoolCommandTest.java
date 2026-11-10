package com.fakerevive.command;

import com.fakerevive.FakeRevivePlugin;
import com.fakerevive.disguise.FakeNamePoolReplenisher;
import com.fakerevive.disguise.ManualRefillResult;
import com.fakerevive.disguise.MojangIdentityFetcher;
import com.fakerevive.disguise.ReplenishSettings;
import com.fakerevive.message.MessageService;
import com.fakerevive.support.PluginLoader;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoolCommandTest {
    private static final Component PREFIX = Component.text("[FakeRevive] ", NamedTextColor.AQUA);

    private ServerMock server;
    private FakeRevivePlugin plugin;
    private PlayerMock operator;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = PluginLoader.load();
        operator = server.addPlayer("Admin");
        operator.setOp(true);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void reportsPoolStatus() {
        int identityCount = plugin.getFakeNamePool().availableCount();

        server.dispatchCommand(operator, "fr pool");

        assertEquals(
                PREFIX.append(Component.text(
                        "Namen-Pool: " + identityCount + " frei, 0 verbraucht (Ziel: 60).",
                        NamedTextColor.YELLOW)),
                operator.nextComponentMessage());
    }

    @Test
    void poolStatusTracksHandedOutNames() {
        PlayerMock target = server.addPlayer("Target");
        int identityCount = plugin.getFakeNamePool().availableCount();
        server.dispatchCommand(operator, "fr disguise Target");
        drainMessages(operator);

        server.dispatchCommand(operator, "fr pool");

        assertEquals(
                PREFIX.append(Component.text(
                        "Namen-Pool: " + (identityCount - 1) + " frei, 1 verbraucht (Ziel: 60).",
                        NamedTextColor.YELLOW)),
                operator.nextComponentMessage());
        assertNotNull(target);
    }

    @Test
    void rejectsUnknownPoolSubcommand() {
        server.dispatchCommand(operator, "fr pool nonsense");

        assertEquals(
                PREFIX.append(Component.text("Benutzung: /fr pool [refill]", NamedTextColor.RED)),
                operator.nextComponentMessage());
    }

    @Test
    void tellsTheSenderWhenTheDisguisePoolIsEmpty() {
        server.addPlayer("Target");
        drainPool();
        drainMessages(operator);

        server.dispatchCommand(operator, "fr disguise Target");

        assertEquals(
                PREFIX.append(Component.text("Der Fake-Namen-Pool ist erschöpft.", NamedTextColor.RED)),
                operator.nextComponentMessage());
    }

    @Test
    void revivesWithoutDisguiseAndWarnsWhenThePoolIsEmpty() {
        PlayerMock victim = server.addPlayer("Victim");
        victim.damage(victim.getHealth() + 1);
        drainPool();
        drainMessages(operator);

        server.dispatchCommand(operator, "fr revive Victim");

        assertEquals(
                PREFIX.append(Component.text("Du hast Victim erfolgreich wiederbelebt!", NamedTextColor.GREEN)),
                operator.nextComponentMessage());
        assertEquals(
                PREFIX.append(Component.text(
                        "Der Namen-Pool ist leer. Victim wurde ohne Verkleidung wiederbelebt. "
                                + "Neue Namen werden im Hintergrund von Mojang geholt.",
                        NamedTextColor.RED)),
                operator.nextComponentMessage());
    }

    @Test
    void refillAnswersWithStartedWhenARefillWasStarted() {
        runRefillWith(ManualRefillResult.STARTED);

        assertEquals(
                PREFIX.append(Component.text("Nachschub von Mojang wurde gestartet.", NamedTextColor.GREEN)),
                operator.nextComponentMessage());
    }

    @Test
    void refillAnswersWithRunningWhenARefillIsAlreadyInProgress() {
        runRefillWith(ManualRefillResult.ALREADY_RUNNING);

        assertEquals(
                PREFIX.append(Component.text("Es läuft bereits ein Nachschub-Vorgang.", NamedTextColor.GRAY)),
                operator.nextComponentMessage());
    }

    @Test
    void refillAnswersWithFullWhenThePoolHasNothingToFetch() {
        runRefillWith(ManualRefillResult.POOL_FULL);

        assertEquals(
                PREFIX.append(Component.text("Der Namen-Pool ist bereits voll (Ziel: 7).", NamedTextColor.YELLOW)),
                operator.nextComponentMessage());
    }

    private void runRefillWith(ManualRefillResult result) {
        MessageService messageService = new MessageService(plugin, "de");
        ReplenishSettings settings = new ReplenishSettings(false, 7, 1, 2, 20L * 60L, Duration.ZERO, 3, false);
        FakeNamePoolReplenisher replenisher = new FakeNamePoolReplenisher(plugin, plugin.getFakeNamePool(),
                new MojangIdentityFetcher(), plugin.getLogger(), messageService, settings) {
            @Override
            public ManualRefillResult requestManualRefill() {
                return result;
            }
        };
        PoolCommandHandler handler = new PoolCommandHandler(messageService, plugin.getFakeNamePool(), replenisher);

        handler.execute(operator, new String[]{"refill"});
    }

    private void drainPool() {
        PlayerMock sink = server.addPlayer("Sink");
        int identityCount = plugin.getFakeNamePool().availableCount();
        for (int index = 0; index < identityCount; index++) {
            server.dispatchCommand(operator, "fr disguise Sink");
        }
        assertTrue(sink.isOnline());
    }

    private void drainMessages(PlayerMock player) {
        while (player.nextComponentMessage() != null) {
        }
    }
}
