package com.deathrevive.plugin.command;

import com.deathrevive.plugin.FakeLeaveAndRevivePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviveCommandTest {

    private ServerMock server;
    private PlayerMock op;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.load(FakeLeaveAndRevivePlugin.class);
        op = server.addPlayer("Admin");
        op.setOp(true);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void deniesUsageWithoutExactlyOneArgument() {
        boolean handled = server.dispatchCommand(op, "revive");

        assertTrue(handled);
        assertEquals(
                Component.text("Benutzung: /revive <Spieler> oder /revive @a", NamedTextColor.RED),
                op.nextComponentMessage());
    }

    @Test
    void deniesReviveForUnknownPlayer() {
        server.dispatchCommand(op, "revive Ghost");

        assertEquals(
                Component.text("Dieser Spieler wurde nicht gefunden.", NamedTextColor.RED),
                op.nextComponentMessage());
    }

    @Test
    void deniesReviveForPlayerNotFakedOut() {
        PlayerMock target = server.addPlayer("Target");

        server.dispatchCommand(op, "revive Target");

        assertEquals(
                Component.text("Dieser Spieler ist nicht im Spectator-Modus!", NamedTextColor.RED),
                op.nextComponentMessage());
    }

    @Test
    void revivesFakedOutPlayerAndClearsFakedOutState() {
        PlayerMock target = server.addPlayer("Target");
        target.damage(target.getHealth() + 1);
        drainMessages(op);

        server.dispatchCommand(op, "revive Target");

        assertEquals(GameMode.SURVIVAL, target.getGameMode());
        assertEquals(
                Component.text("Du hast Target erfolgreich wiederbelebt!", NamedTextColor.GREEN),
                op.nextComponentMessage());

        server.dispatchCommand(op, "revive Target");

        assertEquals(
                Component.text("Dieser Spieler ist nicht im Spectator-Modus!", NamedTextColor.RED),
                op.nextComponentMessage());
    }

    @Test
    void revivesAllFakedOutPlayersWithWildcard() {
        PlayerMock firstVictim = server.addPlayer("FirstVictim");
        PlayerMock secondVictim = server.addPlayer("SecondVictim");
        PlayerMock bystander = server.addPlayer("Bystander");
        firstVictim.damage(firstVictim.getHealth() + 1);
        secondVictim.damage(secondVictim.getHealth() + 1);
        drainMessages(op);

        server.dispatchCommand(op, "revive @a");

        assertEquals(GameMode.SURVIVAL, firstVictim.getGameMode());
        assertEquals(GameMode.SURVIVAL, secondVictim.getGameMode());
        assertEquals(
                Component.text("Es wurden erfolgreich ", NamedTextColor.GREEN)
                        .append(Component.text(2, NamedTextColor.YELLOW))
                        .append(Component.text(" Spieler wiederbelebt!", NamedTextColor.GREEN)),
                op.nextComponentMessage());
        assertEquals(GameMode.SURVIVAL, bystander.getGameMode());
    }

    @Test
    void respondsWhenNoFakedOutPlayersExistForWildcard() {
        server.addPlayer("Bystander");

        server.dispatchCommand(op, "revive @a");

        assertEquals(
                Component.text(
                        "Es gab keine Spieler im Spectator-Modus, die wiederbelebt werden konnten.",
                        NamedTextColor.YELLOW),
                op.nextComponentMessage());
    }

    private void drainMessages(PlayerMock player) {
        while (player.nextComponentMessage() != null) {
            // no-op
        }
    }
}
