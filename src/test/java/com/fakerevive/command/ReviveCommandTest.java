package com.fakerevive.command;

import com.fakerevive.FakeRevivePlugin;
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
    private PlayerMock op;

    @BeforeEach
    void setUp() {
        // MockBukkit executes runTaskAsynchronously immediately on a real thread, so without
        // this stub every revive in these tests would hit the live Mojang API for replenishment.
        FakeRevivePlugin.setIdentityFetcherFactoryForTesting(() -> new MojangIdentityFetcher() {
            @Override
            public Optional<FakeIdentity> fetchNewIdentity(Set<String> excludedNames, int maxAttempts) {
                return Optional.empty();
            }
        });
        server = MockBukkit.mock();
        MockBukkit.load(FakeRevivePlugin.class);
        op = server.addPlayer("Admin");
        op.setOp(true);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
        FakeRevivePlugin.setIdentityFetcherFactoryForTesting(MojangIdentityFetcher::new);
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
        boolean handled = server.dispatchCommand(op, "fr revive");

        assertTrue(handled);
        assertEquals(
                PREFIX.append(Component.text("Benutzung: /fr revive <Spieler/@a/<Team>> [Kit]", NamedTextColor.RED)),
                op.nextComponentMessage());
    }

    @Test
    void deniesReviveForUnknownPlayer() {
        server.dispatchCommand(op, "fr revive Ghost");

        assertEquals(
                PREFIX.append(Component.text("Dieser Spieler wurde nicht gefunden.", NamedTextColor.RED)),
                op.nextComponentMessage());
    }

    @Test
    void deniesReviveForPlayerNotFakedOut() {
        PlayerMock target = server.addPlayer("Target");

        server.dispatchCommand(op, "fr revive Target");

        assertEquals(
                PREFIX.append(Component.text("Dieser Spieler ist nicht im Spectator-Modus!", NamedTextColor.RED)),
                op.nextComponentMessage());
    }

    @Test
    void revivesFakedOutPlayerAndClearsFakedOutState() {
        PlayerMock target = server.addPlayer("Target");
        target.damage(target.getHealth() + 1);
        drainMessages(op);

        server.dispatchCommand(op, "fr revive Target");

        assertEquals(GameMode.SURVIVAL, target.getGameMode());
        assertEquals(
                PREFIX.append(Component.text("Du hast Target erfolgreich wiederbelebt!", NamedTextColor.GREEN)),
                op.nextComponentMessage());

        server.dispatchCommand(op, "fr revive Target");

        assertEquals(
                PREFIX.append(Component.text("Dieser Spieler ist nicht im Spectator-Modus!", NamedTextColor.RED)),
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

        server.dispatchCommand(op, "fr revive @a");

        assertEquals(GameMode.SURVIVAL, firstVictim.getGameMode());
        assertEquals(GameMode.SURVIVAL, secondVictim.getGameMode());
        assertEquals(
                PREFIX.append(Component.text("Es wurden erfolgreich 2 Spieler wiederbelebt!", NamedTextColor.GREEN)),
                op.nextComponentMessage());
        assertEquals(GameMode.SURVIVAL, bystander.getGameMode());
    }

    @Test
    void respondsWhenNoFakedOutPlayersExistForWildcard() {
        server.addPlayer("Bystander");

        server.dispatchCommand(op, "fr revive @a");

        assertEquals(
                PREFIX.append(Component.text(
                        "Es gab keine Spieler im Spectator-Modus, die wiederbelebt werden konnten.",
                        NamedTextColor.YELLOW)),
                op.nextComponentMessage());
    }

    private void drainMessages(PlayerMock player) {
        while (player.nextComponentMessage() != null) {
        }
    }
}
