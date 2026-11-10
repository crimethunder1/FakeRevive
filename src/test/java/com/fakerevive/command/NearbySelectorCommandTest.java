package com.fakerevive.command;

import com.fakerevive.FakeRevivePlugin;
import com.fakerevive.support.PluginLoader;
import com.fakerevive.disguise.ActiveDisguiseRegistry;
import com.fakerevive.disguise.FakeIdentity;
import com.fakerevive.disguise.MojangIdentityFetcher;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
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

class NearbySelectorCommandTest {
    private static final String PREFIX = "[FakeRevive] ";
    private static final String PLAYERS_ONLY = PREFIX + "Nur Spieler können diesen Befehl ausführen.";
    private static final String DISGUISE_NOT_FOUND = PREFIX + "Dieser Spieler wurde nicht gefunden.";
    private static final String REVIVE_NO_TARGETS =
            PREFIX + "Es gab keine Spieler im Spectator-Modus, die wiederbelebt werden konnten.";

    private static final String PER_PLAYER_POOL_EXHAUSTED = PREFIX + "Der Fake-Namen-Pool ist erschöpft.";
    private static final String POOL_EXHAUSTED_ALL_PREFIX = PREFIX + "Der Namen-Pool ist leer. ";
    private static final String POOL_EXHAUSTED_ALL_SUFFIX = " Spieler wurden nicht verkleidet. "
            + "Neue Namen werden im Hintergrund von Mojang geholt.";

    private ServerMock server;
    private FakeRevivePlugin plugin;
    private ConsoleCommandSenderMock console;
    private ActiveDisguiseRegistry registry;
    private PlayerMock admin;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = PluginLoader.load();
        console = server.getConsoleSender();
        registry = plugin.getActiveDisguiseRegistry();
        admin = server.addPlayer("Admin");
        admin.setOp(true);
        world = admin.getWorld();
        admin.teleport(new Location(world, 0, 64, 0));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void nearestSelectorReturnsTheNearestOtherPlayerAndNotTheSender() {
        PlayerMock far = placedPlayer("Far", 10);
        PlayerMock close = placedPlayer("Close", 3);
        placedPlayer("VeryFar", 200);

        Optional<org.bukkit.entity.Player> nearest = NearbyPlayerResolver.resolveNearest(admin, candidate -> true);

        assertTrue(nearest.isPresent());
        assertEquals(close.getUniqueId(), nearest.get().getUniqueId());
        assertFalse(nearest.get().getUniqueId().equals(admin.getUniqueId()));
        assertFalse(nearest.get().getUniqueId().equals(far.getUniqueId()));
    }

    @Test
    void nearestSelectorIsEmptyWithoutAnyOtherPlayer() {
        assertTrue(NearbyPlayerResolver.resolveNearest(admin, candidate -> true).isEmpty());
    }

    @Test
    void nearSelectorReturnsEveryPlayerInRadiusIncludingTheSender() {
        PlayerMock inside = placedPlayer("Inside", 16);
        placedPlayer("Outside", 17);

        List<org.bukkit.entity.Player> nearby = NearbyPlayerResolver.resolveNear(admin, candidate -> true);

        assertEquals(Set.of(admin.getUniqueId(), inside.getUniqueId()),
                Set.copyOf(nearby.stream().map(org.bukkit.entity.Player::getUniqueId).toList()));
    }

    @Test
    void selectorsIgnorePlayersInOtherWorlds() {
        World otherWorld = server.addSimpleWorld("other");
        PlayerMock elsewhere = placedPlayer("Elsewhere", 1);
        elsewhere.teleport(new Location(otherWorld, 0, 64, 0));

        assertTrue(NearbyPlayerResolver.resolveNearest(admin, candidate -> true).isEmpty());
        assertEquals(List.of(admin.getUniqueId()),
                NearbyPlayerResolver.resolveNear(admin, candidate -> true).stream()
                        .map(org.bukkit.entity.Player::getUniqueId).toList());
        assertTrue(NearbyPlayerResolver.resolveNear(admin, candidate -> candidate != admin).isEmpty());
    }

    @Test
    void consoleSenderIsRejectedForEveryNearbySelector() {
        placedPlayer("Target", 1);

        server.dispatchCommand(console, "fr disguise @p");
        server.dispatchCommand(console, "fr disguise @near");
        server.dispatchCommand(console, "fr revive @p");
        server.dispatchCommand(console, "fr revive @near");

        assertEquals(List.of(PLAYERS_ONLY, PLAYERS_ONLY, PLAYERS_ONLY, PLAYERS_ONLY), drain(console));
        assertTrue(registry.getFakeName(server.getPlayer("Target").getUniqueId()).isEmpty());
    }

    @Test
    void disguiseNearestSkipsExemptPlayersAndPicksTheNextOne() {
        PlayerMock exempt = placedPlayer("ExemptClose", 2);
        PlayerMock normal = placedPlayer("NormalFar", 8);
        server.dispatchCommand(admin, "fr exempt add ExemptClose");
        drain(admin);

        server.dispatchCommand(admin, "fr disguise @p");

        assertFalse(registry.getIdentity(exempt.getUniqueId()).isPresent());
        assertTrue(registry.getIdentity(normal.getUniqueId()).isPresent());
        assertFalse(registry.getIdentity(admin.getUniqueId()).isPresent());
    }

    @Test
    void disguiseNearestReportsNotFoundWhenOnlyExemptPlayersAreAround() {
        PlayerMock exempt = placedPlayer("OnlyExempt", 2);
        server.dispatchCommand(admin, "fr exempt add OnlyExempt");
        drain(admin);

        server.dispatchCommand(admin, "fr disguise @p");

        assertEquals(List.of(DISGUISE_NOT_FOUND), drain(admin));
        assertFalse(registry.getIdentity(exempt.getUniqueId()).isPresent());
    }

    @Test
    void disguiseNearSkipsExemptPlayersAndReportsTheirNumber() {
        PlayerMock exempt = placedPlayer("ExemptNear", 2);
        PlayerMock normal = placedPlayer("NormalNear", 4);
        PlayerMock outside = placedPlayer("Outside", 50);
        server.dispatchCommand(admin, "fr exempt add ExemptNear");
        drain(admin);

        server.dispatchCommand(admin, "fr disguise @near");

        assertFalse(registry.getIdentity(exempt.getUniqueId()).isPresent());
        assertTrue(registry.getIdentity(normal.getUniqueId()).isPresent());
        assertTrue(registry.getIdentity(admin.getUniqueId()).isPresent());
        assertFalse(registry.getIdentity(outside.getUniqueId()).isPresent());
        List<String> messages = drain(admin);
        assertEquals(PREFIX + "2 Spieler verkleidet, 1 ausgenommen, 0 bereits verkleidet.",
                messages.get(messages.size() - 1));
    }

    @Test
    void disguiseNearWithEmptyPoolCountsNobodyAndReportsTheRestOnce() {
        PlayerMock sink = placedPlayer("Sink", 200);
        PlayerMock first = placedPlayer("First", 2);
        PlayerMock second = placedPlayer("Second", 4);
        drainPool(sink, plugin.getFakeNamePool().availableCount());
        drain(admin);

        server.dispatchCommand(admin, "fr disguise @near");

        assertEquals(List.of(
                PREFIX + "0 Spieler verkleidet, 0 ausgenommen, 0 bereits verkleidet.",
                POOL_EXHAUSTED_ALL_PREFIX + "3" + POOL_EXHAUSTED_ALL_SUFFIX),
                drain(admin));
        assertFalse(registry.getIdentity(admin.getUniqueId()).isPresent());
        assertFalse(registry.getIdentity(first.getUniqueId()).isPresent());
        assertFalse(registry.getIdentity(second.getUniqueId()).isPresent());
    }

    @Test
    void disguiseNearWithScarcePoolDisguisesOnlyAsManyAsThePoolHolds() {
        PlayerMock sink = placedPlayer("Sink", 200);
        PlayerMock first = placedPlayer("First", 2);
        PlayerMock second = placedPlayer("Second", 4);
        drainPool(sink, plugin.getFakeNamePool().availableCount() - 1);
        drain(admin);

        server.dispatchCommand(admin, "fr disguise @near");

        long disguisedCount = List.of(admin, first, second).stream()
                .filter(player -> registry.getIdentity(player.getUniqueId()).isPresent())
                .count();
        assertEquals(1, disguisedCount);
        List<String> messages = drain(admin);
        assertEquals(PREFIX + "1 Spieler verkleidet, 0 ausgenommen, 0 bereits verkleidet.",
                messages.get(messages.size() - 2));
        assertEquals(POOL_EXHAUSTED_ALL_PREFIX + "2" + POOL_EXHAUSTED_ALL_SUFFIX,
                messages.get(messages.size() - 1));
        assertEquals(1, messages.stream().filter(message -> message.startsWith(POOL_EXHAUSTED_ALL_PREFIX)).count());
        assertFalse(messages.contains(PER_PLAYER_POOL_EXHAUSTED));
    }

    @Test
    void disguiseRandomSkipsExemptPlayers() {
        PlayerMock exempt = placedPlayer("ExemptRandom", 2);
        server.dispatchCommand(admin, "fr exempt add ExemptRandom");
        server.dispatchCommand(admin, "fr exempt add Admin");
        drain(admin);

        server.dispatchCommand(admin, "fr disguise @r");

        assertEquals(List.of(DISGUISE_NOT_FOUND), drain(admin));
        assertFalse(registry.getIdentity(exempt.getUniqueId()).isPresent());

        PlayerMock normal = placedPlayer("NormalRandom", 3);
        server.dispatchCommand(admin, "fr disguise @r");

        assertTrue(registry.getIdentity(normal.getUniqueId()).isPresent());
        assertFalse(registry.getIdentity(exempt.getUniqueId()).isPresent());
        assertFalse(registry.getIdentity(admin.getUniqueId()).isPresent());
    }

    @Test
    void explicitPlayerNameStillIgnoresExempt() {
        PlayerMock exempt = placedPlayer("NamedExempt", 2);
        server.dispatchCommand(admin, "fr exempt add NamedExempt");
        drain(admin);

        server.dispatchCommand(admin, "fr disguise NamedExempt");

        assertTrue(registry.getIdentity(exempt.getUniqueId()).isPresent());
    }

    @Test
    void reviveNearestPicksOnlyFakedOutPlayers() {
        PlayerMock aliveClose = placedPlayer("AliveClose", 2);
        PlayerMock fakedOutFar = placedPlayer("FakedOutFar", 40);
        PlayerMock fakedOutFarther = placedPlayer("FakedOutFarther", 90);
        fakeOut(fakedOutFar, 40);
        fakeOut(fakedOutFarther, 90);
        drain(admin);

        server.dispatchCommand(admin, "fr revive @p");

        assertEquals(GameMode.SURVIVAL, fakedOutFar.getGameMode());
        assertEquals(GameMode.SPECTATOR, fakedOutFarther.getGameMode());
        assertEquals(GameMode.SURVIVAL, aliveClose.getGameMode());
        assertEquals(PREFIX + "Du hast FakedOutFar erfolgreich wiederbelebt!", drain(admin).get(0));
    }

    @Test
    void reviveNearestReportsNoTargetsWithoutAnyFakedOutPlayer() {
        placedPlayer("AliveClose", 2);
        drain(admin);

        server.dispatchCommand(admin, "fr revive @p");

        assertEquals(List.of(REVIVE_NO_TARGETS), drain(admin));
    }

    @Test
    void reviveNearRevivesOnlyFakedOutPlayersInRadius() {
        PlayerMock fakedOutInside = placedPlayer("InsideOut", 5);
        PlayerMock fakedOutOutside = placedPlayer("OutsideOut", 60);
        PlayerMock aliveInside = placedPlayer("InsideAlive", 6);
        fakeOut(fakedOutInside, 5);
        fakeOut(fakedOutOutside, 60);
        drain(admin);

        server.dispatchCommand(admin, "fr revive @near");

        assertEquals(GameMode.SURVIVAL, fakedOutInside.getGameMode());
        assertEquals(GameMode.SPECTATOR, fakedOutOutside.getGameMode());
        assertEquals(GameMode.SURVIVAL, aliveInside.getGameMode());
        assertEquals(PREFIX + "Es wurden erfolgreich 1 Spieler wiederbelebt!", drain(admin).get(0));
    }

    @Test
    void tabCompletionOffersNearWhereverNearestIsOffered() {
        PluginCommand command = plugin.getCommand("fr");
        assertNotNull(command);

        List<String> disguiseOptions = command.tabComplete(admin, "fr", new String[]{"disguise", "@"});
        List<String> reviveOptions = command.tabComplete(admin, "fr", new String[]{"revive", "@"});

        assertTrue(disguiseOptions.contains("@p"));
        assertTrue(disguiseOptions.contains("@near"));
        assertTrue(reviveOptions.contains("@p"));
        assertTrue(reviveOptions.contains("@near"));
        assertEquals(List.of("@near"), command.tabComplete(admin, "fr", new String[]{"disguise", "@ne"}));
    }

    private PlayerMock placedPlayer(String name, double distance) {
        PlayerMock player = server.addPlayer(name);
        player.teleport(new Location(world, distance, 64, 0));
        return player;
    }

    private void drainPool(PlayerMock sink, int count) {
        for (int index = 0; index < count; index++) {
            server.dispatchCommand(console, "fr disguise " + sink.getName());
        }
    }

    private void fakeOut(PlayerMock player, double distance) {
        player.damage(player.getHealth() + 1);
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(new Location(world, distance, 64, 0));
    }

    private List<String> drain(org.bukkit.command.CommandSender receiver) {
        List<String> messages = new ArrayList<>();
        Component message;
        if (receiver instanceof ConsoleCommandSenderMock consoleSender) {
            while ((message = consoleSender.nextComponentMessage()) != null) {
                messages.add(PlainTextComponentSerializer.plainText().serialize(message));
            }
        } else {
            PlayerMock player = (PlayerMock) receiver;
            while ((message = player.nextComponentMessage()) != null) {
                messages.add(PlainTextComponentSerializer.plainText().serialize(message));
            }
        }
        return messages;
    }
}
