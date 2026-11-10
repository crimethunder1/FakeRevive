package com.fakerevive.command;

import com.fakerevive.FakeRevivePlugin;
import com.fakerevive.support.PluginLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TabCompletionSnapshotTest {
    private ServerMock server;
    private FakeRevivePlugin plugin;
    private PlayerMock operator;
    private PlayerMock plainPlayer;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = PluginLoader.load();
        operator = server.addPlayer("Admin");
        operator.setOp(true);
        plainPlayer = server.addPlayer("Plain");
        server.dispatchCommand(operator, "fr kit save alpha");
        server.dispatchCommand(operator, "fr disguise Plain");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void firstLevelForPlayerWithAllPermissions() {
        assertCompletion(operator, List.of("revive", "undisguise", "disguise", "exempt", "kit", "team", "armor",
                "pool", "leave", "reload", "help"), "");
        assertCompletion(operator, List.of("revive", "reload"), "re");
        assertCompletion(operator, List.of(), "x");
    }

    @Test
    void reviveForPlayerWithAllPermissions() {
        assertCompletion(operator, List.of("@a", "@p", "@near", "@r", "Admin", "Plain"), "revive", "");
        assertCompletion(operator, List.of("@a", "@p", "@near", "@r"), "revive", "@");
        assertCompletion(operator, List.of("alpha"), "revive", "Admin", "");
        assertCompletion(operator, List.of(), "revive", "Admin", "x", "");
    }

    @Test
    void undisguiseForPlayerWithAllPermissions() {
        List<String> firstArgument = tabComplete(operator, "undisguise", "");
        assertEquals(List.of("@a", "Admin", "Plain"), firstArgument.subList(0, 3));
        assertEquals(4, firstArgument.size());
        assertCompletion(operator, List.of(), "undisguise", "Admin", "");
    }

    @Test
    void disguiseForPlayerWithAllPermissions() {
        assertCompletion(operator, List.of("@a", "@p", "@near", "@r", "Admin", "Plain"), "disguise", "");
        assertCompletion(operator, List.of(), "disguise", "Admin", "");
    }

    @Test
    void exemptForPlayerWithAllPermissions() {
        assertCompletion(operator, List.of("add", "remove", "list"), "exempt", "");
        assertCompletion(operator, List.of("Admin", "Plain"), "exempt", "add", "");
        assertCompletion(operator, List.of(), "exempt", "remove", "");
        assertCompletion(operator, List.of(), "exempt", "list", "");
        assertCompletion(operator, List.of(), "exempt", "add", "x", "");
    }

    @Test
    void kitForPlayerWithAllPermissions() {
        assertCompletion(operator, List.of("save", "list", "give", "equip", "delete"), "kit", "");
        assertCompletion(operator, List.of("alpha"), "kit", "give", "");
        assertEquals(List.of("@a", "Admin", "Plain"), tabComplete(operator, "kit", "give", "alpha", "").subList(0, 3));
        assertCompletion(operator, List.of(), "kit", "give", "alpha", "x", "");
        assertCompletion(operator, List.of("alpha"), "kit", "equip", "");
        assertCompletion(operator, List.of("@a", "Admin", "Plain"), "kit", "equip", "alpha", "");
        assertCompletion(operator, List.of("alpha"), "kit", "delete", "");
        assertCompletion(operator, List.of(), "kit", "delete", "alpha", "");
        assertCompletion(operator, List.of(), "kit", "save", "");
    }

    @Test
    void armorForPlayerWithAllPermissions() {
        assertCompletion(operator, List.of("on", "off", "status"), "armor", "");
        assertCompletion(operator, List.of("@a", "Admin", "Plain"), "armor", "on", "");
        assertCompletion(operator, List.of("Admin", "Plain"), "armor", "status", "");
        assertCompletion(operator, List.of(), "armor", "on", "x", "");
    }

    @Test
    void poolTeamReloadAndHelpForPlayerWithAllPermissions() {
        assertCompletion(operator, List.of("refill"), "pool", "");
        assertCompletion(operator, List.of(), "pool", "refill", "");
        assertCompletion(operator, List.of(), "team", "");
        assertCompletion(operator, List.of(), "reload", "");
        assertCompletion(operator, List.of(), "help", "");
        assertCompletion(operator, List.of(), "leave", "");
    }

    @Test
    void firstLevelForPlayerWithoutPermissions() {
        assertCompletion(plainPlayer, List.of("leave", "help"), "");
        assertCompletion(plainPlayer, List.of(), "re");
        assertCompletion(plainPlayer, List.of(), "x");
    }

    @Test
    void everyArgumentPositionIsEmptyForPlayerWithoutPermissions() {
        List<List<String>> argumentLists = List.of(
                List.of("revive", ""), List.of("revive", "@"), List.of("revive", "Admin", ""),
                List.of("revive", "Admin", "x", ""),
                List.of("undisguise", ""), List.of("undisguise", "Admin", ""),
                List.of("disguise", ""), List.of("disguise", "Admin", ""),
                List.of("exempt", ""), List.of("exempt", "add", ""), List.of("exempt", "remove", ""),
                List.of("exempt", "list", ""), List.of("exempt", "add", "x", ""),
                List.of("kit", ""), List.of("kit", "give", ""), List.of("kit", "give", "alpha", ""),
                List.of("kit", "give", "alpha", "x", ""), List.of("kit", "equip", ""),
                List.of("kit", "equip", "alpha", ""), List.of("kit", "delete", ""),
                List.of("kit", "delete", "alpha", ""), List.of("kit", "save", ""),
                List.of("armor", ""), List.of("armor", "on", ""), List.of("armor", "status", ""),
                List.of("armor", "on", "x", ""),
                List.of("pool", ""), List.of("pool", "refill", ""),
                List.of("team", ""), List.of("reload", ""), List.of("help", ""), List.of("leave", ""));
        for (List<String> arguments : argumentLists) {
            assertCompletion(plainPlayer, List.of(), arguments.toArray(new String[0]));
        }
    }

    private void assertCompletion(PlayerMock sender, List<String> expected, String... arguments) {
        assertEquals(expected, tabComplete(sender, arguments), "Arguments: " + List.of(arguments));
    }

    private List<String> tabComplete(PlayerMock sender, String... arguments) {
        return plugin.getCommand("fr").tabComplete(sender, "fr", arguments);
    }
}
