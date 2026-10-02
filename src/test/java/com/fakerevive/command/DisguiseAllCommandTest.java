package com.fakerevive.command;

import com.fakerevive.FakeRevivePlugin;
import com.fakerevive.disguise.ActiveDisguiseRegistry;
import com.fakerevive.disguise.FakeIdentity;
import com.fakerevive.disguise.MojangIdentityFetcher;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.PluginCommand;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisguiseAllCommandTest {

    private static final String PREFIX = "[FakeRevive] ";
    private static final int BUNDLED_NAME_COUNT = 201;
    private static final String PER_PLAYER_POOL_EXHAUSTED = PREFIX + "Der Fake-Namen-Pool ist erschöpft.";

    private ServerMock server;
    private FakeRevivePlugin plugin;
    private ConsoleCommandSenderMock console;
    private ActiveDisguiseRegistry registry;

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
        console = server.getConsoleSender();
        registry = plugin.getActiveDisguiseRegistry();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
        FakeRevivePlugin.setIdentityFetcherFactoryForTesting(MojangIdentityFetcher::new);
    }

    @Test
    void disguisesOnlyPlayersThatAreNeitherExemptNorFakedOutNorDisguised() {
        PlayerMock storedExempt = server.addPlayer("StoredExempt");
        PlayerMock configExempt = server.addPlayer("ConfigExempt");
        PlayerMock permissionExempt = server.addPlayer("PermissionExempt");
        PlayerMock alreadyDisguised = server.addPlayer("AlreadyDisguised");
        PlayerMock fakedOut = server.addPlayer("FakedOut");
        PlayerMock normal = server.addPlayer("Normal");

        server.dispatchCommand(console, "fr exempt add StoredExempt");
        plugin.getConfig().set("exempt-players", List.of("configexempt"));
        plugin.saveConfig();
        server.dispatchCommand(console, "fr reload");
        permissionExempt.addAttachment(plugin, "fakerevive.exempt", true);
        server.dispatchCommand(console, "fr disguise AlreadyDisguised");
        String previousFakeName = registry.getFakeName(alreadyDisguised.getUniqueId()).orElseThrow();
        fakedOut.damage(fakedOut.getHealth() + 1);
        drainMessages();

        server.dispatchCommand(console, "fr disguise @a");

        assertTrue(registry.getIdentity(normal.getUniqueId()).isPresent());
        assertFalse(registry.getIdentity(storedExempt.getUniqueId()).isPresent());
        assertFalse(registry.getIdentity(configExempt.getUniqueId()).isPresent());
        assertFalse(registry.getIdentity(permissionExempt.getUniqueId()).isPresent());
        assertFalse(registry.getIdentity(fakedOut.getUniqueId()).isPresent());
        assertEquals(Optional.of(previousFakeName), registry.getFakeName(alreadyDisguised.getUniqueId()));

        String normalFakeName = registry.getFakeName(normal.getUniqueId()).orElseThrow();
        assertEquals(List.of(
                PREFIX + "Normal wurde als \"" + normalFakeName + "\" verkleidet.",
                PREFIX + "1 Spieler verkleidet, 3 ausgenommen, 1 bereits verkleidet."),
                drainMessages());
    }

    @Test
    void emptyPoolProducesExactlyOneSummaryMessage() {
        PlayerMock sink = server.addPlayer("Sink");
        drainPool(BUNDLED_NAME_COUNT);
        PlayerMock first = server.addPlayer("First");
        PlayerMock second = server.addPlayer("Second");
        PlayerMock third = server.addPlayer("Third");
        drainMessages();

        server.dispatchCommand(console, "fr disguise @a");

        List<String> messages = drainMessages();
        assertEquals(List.of(
                PREFIX + "0 Spieler verkleidet, 0 ausgenommen, 1 bereits verkleidet.",
                PREFIX + "Der Namen-Pool ist leer - 3 Spieler wurden nicht verkleidet. "
                        + "Neue Namen werden im Hintergrund von Mojang geholt."),
                messages);
        assertFalse(messages.contains(PER_PLAYER_POOL_EXHAUSTED));
        assertTrue(registry.getIdentity(sink.getUniqueId()).isPresent());
        assertFalse(registry.getIdentity(first.getUniqueId()).isPresent());
        assertFalse(registry.getIdentity(second.getUniqueId()).isPresent());
        assertFalse(registry.getIdentity(third.getUniqueId()).isPresent());
    }

    @Test
    void poolRunningEmptyDuringTheRunSkipsTheRemainingPlayers() {
        server.addPlayer("Sink");
        drainPool(BUNDLED_NAME_COUNT - 1);
        PlayerMock first = server.addPlayer("First");
        PlayerMock second = server.addPlayer("Second");
        PlayerMock third = server.addPlayer("Third");
        drainMessages();

        server.dispatchCommand(console, "fr disguise @a");

        long disguisedCount = List.of(first, second, third).stream()
                .filter(player -> registry.getIdentity(player.getUniqueId()).isPresent())
                .count();
        assertEquals(1, disguisedCount);

        List<String> messages = drainMessages();
        assertEquals(PREFIX + "1 Spieler verkleidet, 0 ausgenommen, 1 bereits verkleidet.",
                messages.get(messages.size() - 2));
        assertEquals(PREFIX + "Der Namen-Pool ist leer - 2 Spieler wurden nicht verkleidet. "
                        + "Neue Namen werden im Hintergrund von Mojang geholt.",
                messages.get(messages.size() - 1));
        assertFalse(messages.contains(PER_PLAYER_POOL_EXHAUSTED));
    }

    @Test
    void rejectsMinecraftNameTogetherWithAllSelector() {
        PlayerMock target = server.addPlayer("Target");

        server.dispatchCommand(console, "fr disguise @a Notch");

        assertEquals(List.of(PREFIX + "Benutzung: /fr disguise <Spieler|@a|@p|@r> [MinecraftName]"),
                drainMessages());
        assertFalse(registry.getIdentity(target.getUniqueId()).isPresent());
    }

    @Test
    void suggestsAllSelectorForDisguise() {
        PlayerMock operator = server.addPlayer("Operator");
        operator.setOp(true);

        PluginCommand command = plugin.getCommand("fr");
        assertNotNull(command);

        assertEquals(List.of("@a"), command.tabComplete(operator, "fr", new String[]{"disguise", "@a"}));
    }

    private void drainPool(int count) {
        for (int index = 0; index < count; index++) {
            server.dispatchCommand(console, "fr disguise Sink");
        }
    }

    private List<String> drainMessages() {
        List<String> messages = new ArrayList<>();
        Component message;
        while ((message = console.nextComponentMessage()) != null) {
            messages.add(PlainTextComponentSerializer.plainText().serialize(message));
        }
        return messages;
    }
}
