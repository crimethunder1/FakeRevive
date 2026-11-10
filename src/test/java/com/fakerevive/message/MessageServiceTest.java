package com.fakerevive.message;

import com.fakerevive.FakeRevivePlugin;
import com.fakerevive.support.PluginLoader;
import com.fakerevive.disguise.FakeIdentity;
import com.fakerevive.disguise.MojangIdentityFetcher;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MessageServiceTest {
    private static final String KEY_WITH_PLACEHOLDERS = "commands.revive.success-single";
    private static final String KEY_WITHOUT_PLACEHOLDERS = "commands.fr.no-permission";
    private static final String MISSING_KEY = "commands.does.not.exist";

    private ServerMock server;
    private FakeRevivePlugin plugin;
    private MessageService messageService;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = PluginLoader.load();
        messageService = new MessageService(plugin, "en");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void thePrefixIsAquaAndSpelledAsExpected() {
        assertEquals(Component.text("[FakeRevive] ", NamedTextColor.AQUA), MessageService.PREFIX);
    }

    @Test
    void prefixedComponentStartsWithThePrefixAndCarriesTheColoredText() {
        Component component = messageService.prefixed(KEY_WITHOUT_PLACEHOLDERS, NamedTextColor.RED);

        Component expected = MessageService.PREFIX.append(
                Component.text(messageService.get(KEY_WITHOUT_PLACEHOLDERS), NamedTextColor.RED));
        assertEquals(expected, component);
    }

    @Test
    void unprefixedComponentHasNoPrefixAndKeepsTheColor() {
        Component component = messageService.unprefixed(KEY_WITHOUT_PLACEHOLDERS, NamedTextColor.GOLD);

        assertEquals(Component.text(messageService.get(KEY_WITHOUT_PLACEHOLDERS), NamedTextColor.GOLD), component);
        assertEquals(0, component.children().size());
    }

    @Test
    void placeholdersAreResolvedInBothVariants() {
        String resolved = "Successfully revived Steve!";
        assertEquals(resolved, messageService.get(KEY_WITH_PLACEHOLDERS, "player", "Steve"));

        assertEquals(MessageService.PREFIX.append(Component.text(resolved, NamedTextColor.GREEN)),
                messageService.prefixed(KEY_WITH_PLACEHOLDERS, NamedTextColor.GREEN, "player", "Steve"));
        assertEquals(Component.text(resolved, NamedTextColor.GREEN),
                messageService.unprefixed(KEY_WITH_PLACEHOLDERS, NamedTextColor.GREEN, "player", "Steve"));
    }

    @Test
    void aMissingKeyFallsBackToTheKeyItself() {
        assertEquals(MISSING_KEY, messageService.get(MISSING_KEY));
        assertEquals(MessageService.PREFIX.append(Component.text(MISSING_KEY, NamedTextColor.RED)),
                messageService.prefixed(MISSING_KEY, NamedTextColor.RED));
        assertEquals(Component.text(MISSING_KEY, NamedTextColor.RED),
                messageService.unprefixed(MISSING_KEY, NamedTextColor.RED));
    }

    @Test
    void sendDeliversThePrefixedMessage() {
        PlayerMock player = server.addPlayer("Receiver");

        messageService.send(player, KEY_WITH_PLACEHOLDERS, NamedTextColor.GREEN, "player", "Steve");

        assertEquals(MessageService.PREFIX.append(
                        Component.text("Successfully revived Steve!", NamedTextColor.GREEN)),
                player.nextComponentMessage());
        assertNull(player.nextComponentMessage());
    }

    @Test
    void sendWithoutPrefixDeliversTheBareMessage() {
        PlayerMock player = server.addPlayer("Receiver");

        messageService.sendWithoutPrefix(player, KEY_WITHOUT_PLACEHOLDERS, NamedTextColor.RED);

        assertEquals(Component.text(messageService.get(KEY_WITHOUT_PLACEHOLDERS), NamedTextColor.RED),
                player.nextComponentMessage());
        assertNull(player.nextComponentMessage());
    }
}
