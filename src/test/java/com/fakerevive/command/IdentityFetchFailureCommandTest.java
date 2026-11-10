package com.fakerevive.command;

import com.fakerevive.FakeRevivePlugin;
import com.fakerevive.support.PluginLoader;
import com.fakerevive.disguise.ActiveDisguiseRegistry;
import com.fakerevive.disguise.FakeIdentity;
import com.fakerevive.disguise.MojangIdentityFetcher;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityFetchFailureCommandTest {
    private static final String PREFIX = "[FakeRevive] ";
    private static final String NAME_NOT_FOUND = PREFIX + "Kein Minecraft-Account mit dem Namen \"Ghost_Name\" gefunden.";
    private static final String UNEXPECTED_ERROR =
            PREFIX + "Es ist ein unerwarteter Fehler aufgetreten. Details stehen in der Server-Konsole.";
    private static final int TICKS_TO_SETTLE = 5;

    private ServerMock server;
    private FakeRevivePlugin plugin;
    private ConsoleCommandSenderMock console;
    private ActiveDisguiseRegistry registry;
    private ScriptedFetcher fetcher;

    @BeforeEach
    void setUp() {
        fetcher = new ScriptedFetcher();
        server = MockBukkit.mock();
        plugin = PluginLoader.load(fetcher);
        console = server.getConsoleSender();
        registry = plugin.getActiveDisguiseRegistry();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void failedFetchKeepsTheExistingDisguiseIntact() {
        PlayerMock target = server.addPlayer("Target");
        server.dispatchCommand(console, "fr disguise Target");
        FakeIdentity previous = registry.getIdentity(target.getUniqueId()).orElseThrow();
        drainMessages();
        fetcher.byNameResult = requestedName -> Optional.empty();

        server.dispatchCommand(console, "fr disguise Target Ghost_Name");
        settle();

        assertEquals(Optional.of(previous), registry.getIdentity(target.getUniqueId()));
        assertTrue(drainMessages().contains(NAME_NOT_FOUND));
    }

    @Test
    void successfulFetchReplacesTheExistingDisguise() {
        PlayerMock target = server.addPlayer("Target");
        server.dispatchCommand(console, "fr disguise Target");
        FakeIdentity replacement = new FakeIdentity("Real_Account", "value", "signature");
        fetcher.byNameResult = requestedName -> Optional.of(replacement);

        server.dispatchCommand(console, "fr disguise Target Real_Account");
        settle();

        assertEquals(Optional.of(replacement), registry.getIdentity(target.getUniqueId()));
    }

    @Test
    void exhaustedPoolKeepsTheExistingDisguiseIntact() {
        PlayerMock target = server.addPlayer("Target");
        int identityCount = plugin.getFakeNamePool().availableCount();
        for (int index = 0; index < identityCount; index++) {
            server.dispatchCommand(console, "fr disguise Target");
        }
        FakeIdentity previous = registry.getIdentity(target.getUniqueId()).orElseThrow();
        drainMessages();

        server.dispatchCommand(console, "fr disguise Target");

        assertEquals(Optional.of(previous), registry.getIdentity(target.getUniqueId()));
        assertEquals(List.of(PREFIX + "Der Fake-Namen-Pool ist erschöpft."), drainMessages());
    }

    @Test
    void unexpectedFetchExceptionStillGivesTheSenderFeedbackAndKeepsTheDisguise() {
        PlayerMock target = server.addPlayer("Target");
        server.dispatchCommand(console, "fr disguise Target");
        FakeIdentity previous = registry.getIdentity(target.getUniqueId()).orElseThrow();
        drainMessages();
        fetcher.byNameResult = requestedName -> {
            throw new IllegalStateException("boom");
        };

        server.dispatchCommand(console, "fr disguise Target Ghost_Name");
        settle();

        assertEquals(Optional.of(previous), registry.getIdentity(target.getUniqueId()));
        assertTrue(drainMessages().contains(UNEXPECTED_ERROR));
    }

    @Test
    void targetThatWentOfflineDuringTheFetchIsNotDisguisedAndSenderIsTold() {
        PlayerMock target = server.addPlayer("Target");
        fetcher.byNameResult = requestedName -> Optional.of(new FakeIdentity("Real_Account", "value", "signature"));
        drainMessages();

        server.dispatchCommand(console, "fr disguise Target Real_Account");
        target.disconnect();
        settle();

        assertFalse(registry.getIdentity(target.getUniqueId()).isPresent());
        assertTrue(drainMessages().contains(PREFIX + "Dieser Spieler wurde nicht gefunden."));
    }

    @Test
    void kitIsAppliedEvenWhenTheFetchFailsAndPlayerStaysRevivedWithoutDisguise() {
        PlayerMock operator = server.addPlayer("Operator");
        operator.setOp(true);
        operator.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));
        server.dispatchCommand(operator, "fr kit save starter");
        PlayerMock target = server.addPlayer("Target");
        fakeOut(target);
        fetcher.byNameResult = requestedName -> Optional.empty();
        drainMessages(operator);

        server.dispatchCommand(operator, "fr revive Target Ghost_Name starter");
        settle();

        assertEquals(GameMode.SURVIVAL, target.getGameMode());
        assertEquals(Material.DIAMOND_SWORD, target.getInventory().getItem(0).getType());
        assertFalse(registry.getIdentity(target.getUniqueId()).isPresent());
        assertTrue(drainMessages(operator).contains(NAME_NOT_FOUND));
    }

    @Test
    void reviveWithCustomNameAppliesTheFetchedIdentityOnSuccess() {
        PlayerMock operator = server.addPlayer("Operator");
        operator.setOp(true);
        PlayerMock target = server.addPlayer("Target");
        fakeOut(target);
        FakeIdentity fetched = new FakeIdentity("Real_Account", "value", "signature");
        fetcher.byNameResult = requestedName -> Optional.of(fetched);
        drainMessages(operator);

        server.dispatchCommand(operator, "fr revive Target Real_Account");
        settle();

        assertEquals(GameMode.SURVIVAL, target.getGameMode());
        assertEquals(Optional.of(fetched), registry.getIdentity(target.getUniqueId()));
    }

    @Test
    void reviveWithCustomNameReportsUnexpectedFetchExceptionToTheSender() {
        PlayerMock operator = server.addPlayer("Operator");
        operator.setOp(true);
        PlayerMock target = server.addPlayer("Target");
        fakeOut(target);
        fetcher.byNameResult = requestedName -> {
            throw new IllegalStateException("boom");
        };
        drainMessages(operator);

        server.dispatchCommand(operator, "fr revive Target Ghost_Name");
        settle();

        assertEquals(GameMode.SURVIVAL, target.getGameMode());
        assertFalse(registry.getIdentity(target.getUniqueId()).isPresent());
        assertTrue(drainMessages(operator).contains(UNEXPECTED_ERROR));
    }

    private void fakeOut(PlayerMock player) {
        player.damage(player.getHealth() + 1);
        try {
            server.getScheduler().performTicks(2L);
        } catch (UnsupportedOperationException exception) {
            return;
        }
    }

    private void settle() {
        server.getScheduler().waitAsyncTasksFinished();
        server.getScheduler().performTicks(TICKS_TO_SETTLE);
    }

    private List<String> drainMessages() {
        List<String> messages = new ArrayList<>();
        Component message;
        while ((message = console.nextComponentMessage()) != null) {
            messages.add(PlainTextComponentSerializer.plainText().serialize(message));
        }
        return messages;
    }

    private List<String> drainMessages(PlayerMock player) {
        List<String> messages = new ArrayList<>();
        Component message;
        while ((message = player.nextComponentMessage()) != null) {
            messages.add(PlainTextComponentSerializer.plainText().serialize(message));
        }
        return messages;
    }

    private static final class ScriptedFetcher extends MojangIdentityFetcher {
        private volatile Function<String, Optional<FakeIdentity>> byNameResult = requestedName -> Optional.empty();

        @Override
        public Optional<FakeIdentity> fetchIdentityByName(String name) {
            return byNameResult.apply(name);
        }

        @Override
        public Optional<FakeIdentity> fetchNewIdentity(Set<String> excludedNames, int maxAttempts) {
            return Optional.empty();
        }
    }
}
