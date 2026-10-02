package com.fakerevive.command;

import com.fakerevive.FakeRevivePlugin;
import com.fakerevive.disguise.FakeIdentity;
import com.fakerevive.disguise.MojangIdentityFetcher;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.PluginCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubcommandPermissionTest {

    private static final Component PREFIX = Component.text("[FakeRevive] ", NamedTextColor.AQUA);
    private static final Component NO_PERMISSION =
            Component.text("Du hast keine Berechtigung für diesen Befehl.", NamedTextColor.RED);
    private static final Component USAGE = PREFIX.append(Component.text(
            "Benutzung: /fr <revive|undisguise|disguise|exempt|kit|team|armor|pool|leave|reload|help>",
            NamedTextColor.RED));
    private static final List<String> ALL_SUBCOMMANDS = List.of(
            "revive", "undisguise", "disguise", "exempt", "kit", "team", "armor", "pool", "leave", "reload", "help");

    private ServerMock server;
    private FakeRevivePlugin plugin;
    private PlayerMock player;

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
        player = server.addPlayer("Helper");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
        FakeRevivePlugin.setIdentityFetcherFactoryForTesting(MojangIdentityFetcher::new);
    }

    @Test
    void kitNodeAllowsKitList() {
        player.addAttachment(plugin, "fakerevive.kit", true);

        server.dispatchCommand(player, "fr kit list");

        assertEquals(
                PREFIX.append(Component.text("Keine Kits vorhanden.", NamedTextColor.YELLOW)),
                player.nextComponentMessage());
    }

    @Test
    void kitNodeDoesNotAllowPool() {
        player.addAttachment(plugin, "fakerevive.kit", true);

        server.dispatchCommand(player, "fr pool");

        assertEquals(NO_PERMISSION, player.nextComponentMessage());
        assertNull(player.nextComponentMessage());
    }

    @Test
    void kitNodeLimitsTabCompletionToAllowedSubcommands() {
        player.addAttachment(plugin, "fakerevive.kit", true);
        player.addAttachment(plugin, "fakerevive.leave", false);

        assertEquals(List.of("kit", "help"), tabComplete(player, ""));
        assertEquals(List.of("save", "list", "give", "equip", "delete"), tabComplete(player, "kit", ""));
        assertEquals(List.of(), tabComplete(player, "pool", ""));
    }

    @Test
    void adminNodeAllowsEverySubcommand() {
        player.addAttachment(plugin, "fakerevive.admin", true);

        for (String subcommand : ALL_SUBCOMMANDS) {
            if (subcommand.equals("leave")) {
                continue;
            }
            drainMessages(player);
            server.dispatchCommand(player, "fr " + subcommand);
            assertNotEquals(NO_PERMISSION, player.nextComponentMessage(), "fr " + subcommand);
        }
        assertEquals(ALL_SUBCOMMANDS, tabComplete(player, ""));
    }

    @Test
    void adminNodeGrantsEveryChildNode() {
        player.addAttachment(plugin, "fakerevive.admin", true);

        for (String node : List.of("fakerevive.revive", "fakerevive.disguise", "fakerevive.kit", "fakerevive.team",
                "fakerevive.armor", "fakerevive.pool", "fakerevive.reload")) {
            assertTrue(player.hasPermission(node), node);
        }
    }

    @Test
    void playerWithoutAnyNodeIsDenied() {
        player.addAttachment(plugin, "fakerevive.leave", false);

        for (String subcommand : ALL_SUBCOMMANDS) {
            if (subcommand.equals("leave")) {
                continue;
            }
            drainMessages(player);
            server.dispatchCommand(player, "fr " + subcommand);
            assertEquals(NO_PERMISSION, player.nextComponentMessage(), "fr " + subcommand);
        }
        assertEquals(List.of(), tabComplete(player, ""));
        assertEquals(List.of(), tabComplete(player, "kit", ""));
        assertEquals(List.of(), tabComplete(player, "revive", ""));
    }

    @Test
    void unknownSubcommandShowsUsageEvenWithoutAnyNode() {
        player.addAttachment(plugin, "fakerevive.leave", false);

        server.dispatchCommand(player, "fr nonsense");

        assertEquals(USAGE, player.nextComponentMessage());
    }

    @Test
    void leaveStillWorksWithLeaveNode() {
        player.addAttachment(plugin, "fakerevive.leave", true);

        server.dispatchCommand(player, "fr leave");

        assertEquals(
                PREFIX.append(Component.text("Du bist in keinem Team.", NamedTextColor.RED)),
                player.nextComponentMessage());
        assertEquals(List.of("leave", "help"), tabComplete(player, ""));
    }

    @Test
    void leaveIsDeniedWithoutLeaveNode() {
        player.addAttachment(plugin, "fakerevive.leave", false);

        server.dispatchCommand(player, "fr leave");

        assertEquals(
                PREFIX.append(Component.text("Dafür hast du keine Berechtigung.", NamedTextColor.RED)),
                player.nextComponentMessage());
    }

    @Test
    void helpShowsOnlyAllowedLines() {
        player.addAttachment(plugin, "fakerevive.kit", true);
        player.addAttachment(plugin, "fakerevive.pool", true);
        player.addAttachment(plugin, "fakerevive.leave", false);

        server.dispatchCommand(player, "fr help");

        assertEquals(List.of(
                helpLine("Hilfe", NamedTextColor.GOLD),
                helpLine("/fr kit save <Name>: Inventar als Kit speichern", NamedTextColor.YELLOW),
                helpLine("/fr kit list: Alle Kits anzeigen", NamedTextColor.YELLOW),
                helpLine("/fr kit delete <Name>: Kit löschen", NamedTextColor.YELLOW),
                helpLine("/fr kit give <Kit> <Spieler>: Kit an Spieler geben", NamedTextColor.YELLOW),
                helpLine("/fr kit equip <Kit|Team> [Spieler|Team]: Kit ausrüsten", NamedTextColor.YELLOW),
                helpLine("/fr pool [refill]: Namen-Pool anzeigen oder nachfüllen", NamedTextColor.YELLOW),
                helpLine("/fr help: Hilfe anzeigen", NamedTextColor.YELLOW)),
                drainMessages(player));
    }

    @Test
    void helpWithOnlyLeaveNodeShowsLeaveLine() {
        player.addAttachment(plugin, "fakerevive.leave", true);

        server.dispatchCommand(player, "fr help");

        assertEquals(List.of(
                helpLine("Hilfe", NamedTextColor.GOLD),
                helpLine("/fr leave: Eigenes Team verlassen", NamedTextColor.YELLOW),
                helpLine("/fr help: Hilfe anzeigen", NamedTextColor.YELLOW)),
                drainMessages(player));
    }

    private Component helpLine(String text, NamedTextColor color) {
        return PREFIX.append(Component.text(text, color));
    }

    private List<String> tabComplete(PlayerMock sender, String... args) {
        PluginCommand command = plugin.getCommand("fr");
        assertNotNull(command);
        return command.tabComplete(sender, "fr", args);
    }

    private List<Component> drainMessages(PlayerMock target) {
        List<Component> messages = new ArrayList<>();
        Component message;
        while ((message = target.nextComponentMessage()) != null) {
            messages.add(message);
        }
        return messages;
    }
}
