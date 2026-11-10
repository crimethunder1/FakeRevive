package com.fakerevive.command;

import com.fakerevive.FakeRevivePlugin;
import com.fakerevive.support.PluginLoader;
import com.fakerevive.disguise.ActiveDisguiseRegistry;
import com.fakerevive.disguise.FakeIdentity;
import com.fakerevive.disguise.MojangIdentityFetcher;
import com.fakerevive.team.TeamManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetResolverTest {
    private ServerMock server;
    private ActiveDisguiseRegistry registry;
    private TeamManager teamManager;
    private TargetResolver resolver;
    private PlayerMock alice;
    private PlayerMock bob;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        FakeRevivePlugin plugin = PluginLoader.load();
        teamManager = new TeamManager(plugin);
        teamManager.loadTeams();
        registry = new ActiveDisguiseRegistry();
        resolver = new TargetResolver(teamManager, registry);
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void recognizesAllSelectorIgnoringCase() {
        assertTrue(resolver.isAllSelector("@a"));
        assertTrue(resolver.isAllSelector("@A"));
        assertFalse(resolver.isAllSelector("Alice"));
    }

    @Test
    void resolvesAllOnlinePlayers() {
        assertEquals(Set.of(alice, bob), Set.copyOf(resolver.resolveAllOnline()));
    }

    @Test
    void resolvesTeamMembersAndOnlineMembers() {
        teamManager.createTeam("red");
        teamManager.addPlayer(alice.getUniqueId(), "red");
        teamManager.addPlayer(java.util.UUID.randomUUID(), "red");

        assertTrue(resolver.isTeam("red"));
        assertFalse(resolver.isTeam("blue"));
        assertEquals(2, resolver.resolveTeamMemberIds("red").size());
        assertEquals(List.of(alice), resolver.resolveOnlineTeamMembers("red"));
    }

    @Test
    void resolvesPlayerByNameOnly() {
        registry.assign(bob.getUniqueId(), new FakeIdentity("Shadow", "value", "signature"));

        assertEquals(Optional.of(alice), resolver.resolvePlayer("Alice"));
        assertEquals(Optional.empty(), resolver.resolvePlayer("Shadow"));
        assertEquals(Optional.empty(), resolver.resolvePlayer("Nobody"));
    }

    @Test
    void resolvesPlayerByFakeName() {
        registry.assign(bob.getUniqueId(), new FakeIdentity("Shadow", "value", "signature"));

        assertEquals(Optional.of(bob), resolver.resolvePlayerOrFakeName("Shadow"));
        assertEquals(Optional.of(alice), resolver.resolvePlayerOrFakeName("Alice"));
        assertEquals(Optional.empty(), resolver.resolvePlayerOrFakeName("Nobody"));
    }

    @Test
    void buildsCompletionOptions() {
        teamManager.createTeam("red");
        registry.assign(bob.getUniqueId(), new FakeIdentity("Shadow", "value", "signature"));

        List<String> withGroups = resolver.completionOptions(true, false);
        assertEquals("@a", withGroups.get(0));
        assertEquals("red", withGroups.get(1));
        assertTrue(withGroups.containsAll(List.of("Alice", "Bob")));
        assertFalse(withGroups.contains("Shadow"));

        List<String> playersOnly = resolver.completionOptions(false, false);
        assertFalse(playersOnly.contains("@a"));
        assertFalse(playersOnly.contains("red"));

        assertTrue(resolver.completionOptions(true, true).contains("Shadow"));
    }
}
