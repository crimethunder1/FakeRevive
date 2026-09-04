package com.fakerevive.command;

import com.fakerevive.FakeRevivePlugin;
import com.fakerevive.disguise.FakeIdentity;
import com.fakerevive.disguise.MojangIdentityFetcher;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Covers the "/fr kit equip &lt;team&gt;" shorthand that pulls the team's own assigned kit. */
class KitEquipTeamTest {

    private static final Component PREFIX = Component.text("[FakeRevive] ", NamedTextColor.AQUA);

    private ServerMock server;
    private FakeRevivePlugin plugin;
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
        plugin = MockBukkit.load(FakeRevivePlugin.class);
        op = server.addPlayer("Admin");
        op.setOp(true);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
        FakeRevivePlugin.setIdentityFetcherFactoryForTesting(MojangIdentityFetcher::new);
    }

    /** Saves a kit whose only content is a diamond helmet, by snapshotting the admin's inventory. */
    private void saveHelmetKit(String kitName) {
        op.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        server.dispatchCommand(op, "fr kit save " + kitName);
        op.nextComponentMessage();
        op.getInventory().setHelmet(null);
    }

    private void writeTeam(String name, String kit, List<String> memberUuids) {
        File teamsFile = new File(plugin.getDataFolder(), "teams.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(teamsFile);
        config.set("teams." + name + ".kit", kit);
        config.set("teams." + name + ".members", memberUuids);
        try {
            plugin.getDataFolder().mkdirs();
            config.save(teamsFile);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        server.dispatchCommand(op, "fr reload");
        op.nextComponentMessage();
    }

    @Test
    void equipsTheTeamsOwnKitToAllMembers() {
        PlayerMock first = server.addPlayer("First");
        PlayerMock second = server.addPlayer("Second");
        saveHelmetKit("teamkit");
        writeTeam("rot", "teamkit", List.of(first.getUniqueId().toString(), second.getUniqueId().toString()));

        server.dispatchCommand(op, "fr kit equip rot");

        assertEquals(
                PREFIX.append(Component.text(
                        "Kit \"teamkit\" wurde an 2 Spieler aus Team \"rot\" ausgerüstet.", NamedTextColor.GREEN)),
                op.nextComponentMessage());
        assertNotNull(first.getInventory().getHelmet());
        assertEquals(Material.DIAMOND_HELMET, first.getInventory().getHelmet().getType());
        assertEquals(Material.DIAMOND_HELMET, second.getInventory().getHelmet().getType());
    }

    @Test
    void reportsWhenTheTeamHasNoKitAssigned() {
        PlayerMock member = server.addPlayer("Member");
        writeTeam("rot", null, List.of(member.getUniqueId().toString()));

        server.dispatchCommand(op, "fr kit equip rot");

        assertEquals(
                PREFIX.append(Component.text("Team \"rot\" hat kein Kit zugewiesen.", NamedTextColor.RED)),
                op.nextComponentMessage());
        assertNull(member.getInventory().getHelmet());
    }

    @Test
    void reportsWhenTheTeamsKitReferenceIsStale() {
        PlayerMock member = server.addPlayer("Member");
        saveHelmetKit("teamkit");
        writeTeam("rot", "teamkit", List.of(member.getUniqueId().toString()));
        server.dispatchCommand(op, "fr kit delete teamkit");
        op.nextComponentMessage();

        server.dispatchCommand(op, "fr kit equip rot");

        assertEquals(
                PREFIX.append(Component.text("Kit \"teamkit\" wurde nicht gefunden.", NamedTextColor.RED)),
                op.nextComponentMessage());
        assertNull(member.getInventory().getHelmet());
    }

    @Test
    void aTeamNameWinsOverAnEquallyNamedKit() {
        PlayerMock member = server.addPlayer("Member");
        saveHelmetKit("rot");
        saveHelmetKit("teamkit");
        writeTeam("rot", "teamkit", List.of(member.getUniqueId().toString()));

        server.dispatchCommand(op, "fr kit equip rot");

        // "rot" also exists as a kit, but the team branch is checked first.
        assertEquals(
                PREFIX.append(Component.text(
                        "Kit \"teamkit\" wurde an 1 Spieler aus Team \"rot\" ausgerüstet.", NamedTextColor.GREEN)),
                op.nextComponentMessage());
        assertEquals(Material.DIAMOND_HELMET, member.getInventory().getHelmet().getType());
    }

    @Test
    void stillEquipsAKitOntoTheSenderWhenNoTeamMatches() {
        saveHelmetKit("solo");

        server.dispatchCommand(op, "fr kit equip solo");

        assertEquals(
                PREFIX.append(Component.text("Kit \"solo\" wurde ausgerüstet.", NamedTextColor.GREEN)),
                op.nextComponentMessage());
        assertEquals(Material.DIAMOND_HELMET, op.getInventory().getHelmet().getType());
    }

    @Test
    void stillEquipsAnExplicitKitOntoATeam() {
        PlayerMock member = server.addPlayer("Member");
        saveHelmetKit("other");
        writeTeam("rot", null, List.of(member.getUniqueId().toString()));

        server.dispatchCommand(op, "fr kit equip other rot");

        assertEquals(
                PREFIX.append(Component.text(
                        "Kit \"other\" wurde an 1 Spieler aus Team \"rot\" ausgerüstet.", NamedTextColor.GREEN)),
                op.nextComponentMessage());
        assertEquals(Material.DIAMOND_HELMET, member.getInventory().getHelmet().getType());
    }

    @Test
    void worksFromTheConsole() {
        PlayerMock member = server.addPlayer("Member");
        saveHelmetKit("teamkit");
        writeTeam("rot", "teamkit", List.of(member.getUniqueId().toString()));

        server.dispatchCommand(server.getConsoleSender(), "fr kit equip rot");

        assertEquals(Material.DIAMOND_HELMET, member.getInventory().getHelmet().getType());
    }
}
