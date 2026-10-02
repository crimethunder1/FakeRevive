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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExemptCommandTest {

    private static final Component PREFIX = Component.text("[FakeRevive] ", NamedTextColor.AQUA);

    private ServerMock server;
    private FakeRevivePlugin plugin;
    private PlayerMock helper;

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
        helper = server.addPlayer("Helper");
        helper.addAttachment(plugin, "fakerevive.disguise", true);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
        FakeRevivePlugin.setIdentityFetcherFactoryForTesting(MojangIdentityFetcher::new);
    }

    @Test
    void addsOnlinePlayerOnce() {
        server.addPlayer("Target");

        server.dispatchCommand(helper, "fr exempt add Target");
        server.dispatchCommand(helper, "fr exempt add target");

        assertEquals(List.of(
                message("Target ist jetzt von /fr disguise @a ausgenommen.", NamedTextColor.GREEN),
                message("Target steht bereits auf der Ausnahmeliste.", NamedTextColor.YELLOW)),
                drainMessages(helper));
    }

    @Test
    void addsOfflinePlayerTheServerHasSeen() {
        PlayerMock offline = server.addPlayer("Offline");
        offline.disconnect();

        server.dispatchCommand(helper, "fr exempt add Offline");

        assertEquals(List.of(message("Offline ist jetzt von /fr disguise @a ausgenommen.", NamedTextColor.GREEN)),
                drainMessages(helper));
    }

    @Test
    void rejectsUnknownPlayer() {
        server.dispatchCommand(helper, "fr exempt add NeverSeen");

        assertEquals(List.of(message("Spieler \"NeverSeen\" wurde nicht gefunden.", NamedTextColor.RED)),
                drainMessages(helper));
    }

    @Test
    void removesStoredPlayer() {
        server.addPlayer("Target");
        server.dispatchCommand(helper, "fr exempt add Target");
        drainMessages(helper);

        server.dispatchCommand(helper, "fr exempt remove target");
        server.dispatchCommand(helper, "fr exempt remove Target");

        assertEquals(List.of(
                message("Target wurde von der Ausnahmeliste entfernt.", NamedTextColor.GREEN),
                message("Target steht nicht auf der Ausnahmeliste.", NamedTextColor.RED)),
                drainMessages(helper));
    }

    @Test
    void configEntriesAreListedButCannotBeRemoved() {
        server.addPlayer("Stored");
        server.dispatchCommand(helper, "fr exempt add Stored");
        setConfiguredNames(List.of("ConfigOnly"));
        drainMessages(helper);

        server.dispatchCommand(helper, "fr exempt list");
        server.dispatchCommand(helper, "fr exempt remove configonly");

        assertEquals(List.of(
                message("Ausgenommene Spieler: Stored, ConfigOnly (config.yml)", NamedTextColor.GREEN),
                message("configonly steht in der config.yml und kann nur dort entfernt werden.", NamedTextColor.YELLOW)),
                drainMessages(helper));
    }

    @Test
    void listsNothingWhenEmpty() {
        server.dispatchCommand(helper, "fr exempt list");

        assertEquals(List.of(message("Es sind keine Spieler ausgenommen.", NamedTextColor.YELLOW)),
                drainMessages(helper));
    }

    @Test
    void storedPlayersSurviveReload() {
        server.addPlayer("Target");
        server.dispatchCommand(helper, "fr exempt add Target");
        PlayerMock operator = server.addPlayer("Operator");
        operator.setOp(true);
        server.dispatchCommand(operator, "fr reload");
        drainMessages(helper);

        server.dispatchCommand(helper, "fr exempt list");

        assertEquals(List.of(message("Ausgenommene Spieler: Target", NamedTextColor.GREEN)), drainMessages(helper));
    }

    @Test
    void showsUsageForInvalidArguments() {
        server.dispatchCommand(helper, "fr exempt");
        server.dispatchCommand(helper, "fr exempt add");
        server.dispatchCommand(helper, "fr exempt nonsense Target");

        Component usage = message("Benutzung: /fr exempt <add|remove|list> [Spieler]", NamedTextColor.RED);
        assertEquals(List.of(usage, usage, usage), drainMessages(helper));
    }

    @Test
    void deniesPlayersWithoutDisguiseNode() {
        PlayerMock guest = server.addPlayer("Guest");
        guest.addAttachment(plugin, "fakerevive.kit", true);

        server.dispatchCommand(guest, "fr exempt list");

        assertEquals(List.of(Component.text("Du hast keine Berechtigung für diesen Befehl.", NamedTextColor.RED)),
                drainMessages(guest));
        assertTrue(tabComplete(guest, "ex").isEmpty());
    }

    @Test
    void tabCompletionSuggestsOnlinePlayersForAddAndStoredPlayersForRemove() {
        server.addPlayer("Online");
        PlayerMock offline = server.addPlayer("Offline");
        server.dispatchCommand(helper, "fr exempt add Offline");
        offline.disconnect();

        assertEquals(List.of("exempt"), tabComplete(helper, "ex"));
        assertEquals(List.of("add", "remove", "list"), tabComplete(helper, "exempt", ""));
        assertEquals(List.of("Helper", "Online"), tabComplete(helper, "exempt", "add", ""));
        assertEquals(List.of("Offline"), tabComplete(helper, "exempt", "remove", ""));
    }

    private void setConfiguredNames(List<String> names) {
        plugin.getConfig().set("exempt-players", names);
        plugin.saveConfig();
        PlayerMock operator = server.addPlayer("ConfigOperator");
        operator.setOp(true);
        server.dispatchCommand(operator, "fr reload");
    }

    private Component message(String text, NamedTextColor color) {
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
