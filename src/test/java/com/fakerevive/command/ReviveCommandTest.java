package com.fakerevive.command;

import com.fakerevive.FakeRevivePlugin;
import com.fakerevive.support.PluginLoader;
import com.fakerevive.disguise.FakeIdentity;
import com.fakerevive.disguise.MojangIdentityFetcher;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviveCommandTest {
    private static final Component PREFIX = Component.text("[FakeRevive] ", NamedTextColor.AQUA);

    private ServerMock server;
    private PlayerMock operator;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        PluginLoader.load();
        operator = server.addPlayer("Admin");
        operator.setOp(true);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void deniesCommandForSenderWithoutPermission() {
        PlayerMock guest = server.addPlayer("Guest");

        boolean handled = server.dispatchCommand(guest, "fr revive Target");

        assertTrue(handled);
        assertEquals(
                Component.text("Du hast keine Berechtigung für diesen Befehl.", NamedTextColor.RED),
                guest.nextComponentMessage());
    }

    @Test
    void deniesUsageWithoutExactlyOneArgument() {
        boolean handled = server.dispatchCommand(operator, "fr revive");

        assertTrue(handled);
        assertEquals(
                PREFIX.append(Component.text("Benutzung: /fr revive <Spieler/@a/<Team>> [Kit]", NamedTextColor.RED)),
                operator.nextComponentMessage());
    }

    @Test
    void deniesReviveForUnknownPlayer() {
        server.dispatchCommand(operator, "fr revive Ghost");

        assertEquals(
                PREFIX.append(Component.text("Dieser Spieler wurde nicht gefunden.", NamedTextColor.RED)),
                operator.nextComponentMessage());
    }

    @Test
    void deniesReviveForPlayerNotFakedOut() {
        PlayerMock target = server.addPlayer("Target");

        server.dispatchCommand(operator, "fr revive Target");

        assertEquals(
                PREFIX.append(Component.text("Dieser Spieler ist nicht im Spectator-Modus!", NamedTextColor.RED)),
                operator.nextComponentMessage());
    }

    @Test
    void revivesFakedOutPlayerAndClearsFakedOutState() {
        PlayerMock target = server.addPlayer("Target");
        target.damage(target.getHealth() + 1);
        drainMessages(operator);

        server.dispatchCommand(operator, "fr revive Target");

        assertEquals(GameMode.SURVIVAL, target.getGameMode());
        assertEquals(
                PREFIX.append(Component.text("Du hast Target erfolgreich wiederbelebt!", NamedTextColor.GREEN)),
                operator.nextComponentMessage());

        server.dispatchCommand(operator, "fr revive Target");

        assertEquals(
                PREFIX.append(Component.text("Dieser Spieler ist nicht im Spectator-Modus!", NamedTextColor.RED)),
                operator.nextComponentMessage());
    }

    @Test
    void revivesAllFakedOutPlayersWithWildcard() {
        PlayerMock firstVictim = server.addPlayer("FirstVictim");
        PlayerMock secondVictim = server.addPlayer("SecondVictim");
        PlayerMock bystander = server.addPlayer("Bystander");
        firstVictim.damage(firstVictim.getHealth() + 1);
        secondVictim.damage(secondVictim.getHealth() + 1);
        drainMessages(operator);

        server.dispatchCommand(operator, "fr revive @a");

        assertEquals(GameMode.SURVIVAL, firstVictim.getGameMode());
        assertEquals(GameMode.SURVIVAL, secondVictim.getGameMode());
        assertEquals(
                PREFIX.append(Component.text("Es wurden erfolgreich 2 Spieler wiederbelebt!", NamedTextColor.GREEN)),
                operator.nextComponentMessage());
        assertEquals(GameMode.SURVIVAL, bystander.getGameMode());
    }

    @Test
    void respondsWhenNoFakedOutPlayersExistForWildcard() {
        server.addPlayer("Bystander");

        server.dispatchCommand(operator, "fr revive @a");

        assertEquals(
                PREFIX.append(Component.text(
                        "Es gab keine Spieler im Spectator-Modus, die wiederbelebt werden konnten.",
                        NamedTextColor.YELLOW)),
                operator.nextComponentMessage());
    }

    private void drainMessages(PlayerMock player) {
        while (player.nextComponentMessage() != null) {
        }
    }
}
