package com.fakerevive.command;

import com.fakerevive.FakeRevivePlugin;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoolCommandTest {

    private static final Component PREFIX = Component.text("[FakeRevive] ", NamedTextColor.AQUA);
    /** The bundled names.json ships this many identities. */
    private static final int BUNDLED_NAME_COUNT = 201;

    private ServerMock server;
    private PlayerMock op;

    @BeforeEach
    void setUp() {
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
    void reportsPoolStatus() {
        server.dispatchCommand(op, "fr pool");

        assertEquals(
                PREFIX.append(Component.text(
                        "Namen-Pool: " + BUNDLED_NAME_COUNT + " frei, 0 verbraucht (Ziel: 60).",
                        NamedTextColor.YELLOW)),
                op.nextComponentMessage());
    }

    @Test
    void poolStatusTracksHandedOutNames() {
        PlayerMock target = server.addPlayer("Target");
        server.dispatchCommand(op, "fr disguise Target");
        drainMessages(op);

        server.dispatchCommand(op, "fr pool");

        assertEquals(
                PREFIX.append(Component.text(
                        "Namen-Pool: " + (BUNDLED_NAME_COUNT - 1) + " frei, 1 verbraucht (Ziel: 60).",
                        NamedTextColor.YELLOW)),
                op.nextComponentMessage());
        assertNotNull(target);
    }

    @Test
    void rejectsUnknownPoolSubcommand() {
        server.dispatchCommand(op, "fr pool nonsense");

        assertEquals(
                PREFIX.append(Component.text("Benutzung: /fr pool [refill]", NamedTextColor.RED)),
                op.nextComponentMessage());
    }

    /**
     * With the pool drained, a disguise must tell the sender rather than failing silently. This is
     * the state the server was stuck in: names ran out and nothing surfaced it at the prompt.
     */
    @Test
    void tellsTheSenderWhenTheDisguisePoolIsEmpty() {
        server.addPlayer("Target");
        drainPool();
        drainMessages(op);

        server.dispatchCommand(op, "fr disguise Target");

        assertEquals(
                PREFIX.append(Component.text("Der Fake-Namen-Pool ist erschöpft.", NamedTextColor.RED)),
                op.nextComponentMessage());
    }

    /**
     * An exhausted pool must still revive the player - just without a disguise - and say so.
     */
    @Test
    void revivesWithoutDisguiseAndWarnsWhenThePoolIsEmpty() {
        PlayerMock victim = server.addPlayer("Victim");
        victim.damage(victim.getHealth() + 1);
        drainPool();
        drainMessages(op);

        server.dispatchCommand(op, "fr revive Victim");

        assertEquals(
                PREFIX.append(Component.text("Du hast Victim erfolgreich wiederbelebt!", NamedTextColor.GREEN)),
                op.nextComponentMessage());
        assertEquals(
                PREFIX.append(Component.text(
                        "Der Namen-Pool ist leer - Victim wurde ohne Verkleidung wiederbelebt. "
                                + "Neue Namen werden im Hintergrund von Mojang geholt.",
                        NamedTextColor.RED)),
                op.nextComponentMessage());
    }

    /** Hands out every bundled identity so the pool is empty for the test that follows. */
    private void drainPool() {
        PlayerMock sink = server.addPlayer("Sink");
        for (int i = 0; i < BUNDLED_NAME_COUNT; i++) {
            server.dispatchCommand(op, "fr disguise Sink");
        }
        assertTrue(sink.isOnline());
    }

    private void drainMessages(PlayerMock player) {
        while (player.nextComponentMessage() != null) {
        }
    }
}
