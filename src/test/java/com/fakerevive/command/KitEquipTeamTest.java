package com.fakerevive.command;

import com.fakerevive.FakeRevivePlugin;
import com.fakerevive.support.PluginLoader;
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

class KitEquipTeamTest {
    private static final Component PREFIX = Component.text("[FakeRevive] ", NamedTextColor.AQUA);

    private ServerMock server;
    private FakeRevivePlugin plugin;
    private PlayerMock operator;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = PluginLoader.load();
        operator = server.addPlayer("Admin");
        operator.setOp(true);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void saveHelmetKit(String kitName) {
        operator.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        server.dispatchCommand(operator, "fr kit save " + kitName);
        operator.nextComponentMessage();
        operator.getInventory().setHelmet(null);
    }

    private void writeTeam(String name, String kit, List<String> memberUuids) {
        File teamsFile = new File(plugin.getDataFolder(), "teams.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(teamsFile);
        config.set("teams." + name + ".kit", kit);
        config.set("teams." + name + ".members", memberUuids);
        try {
            plugin.getDataFolder().mkdirs();
            config.save(teamsFile);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        server.dispatchCommand(operator, "fr reload");
        operator.nextComponentMessage();
    }

    @Test
    void equipsTheTeamsOwnKitToAllMembers() {
        PlayerMock first = server.addPlayer("First");
        PlayerMock second = server.addPlayer("Second");
        saveHelmetKit("teamkit");
        writeTeam("rot", "teamkit", List.of(first.getUniqueId().toString(), second.getUniqueId().toString()));

        server.dispatchCommand(operator, "fr kit equip rot");

        assertEquals(
                PREFIX.append(Component.text(
                        "Kit \"teamkit\" wurde an 2 Spieler aus Team \"rot\" ausgerüstet.", NamedTextColor.GREEN)),
                operator.nextComponentMessage());
        assertNotNull(first.getInventory().getHelmet());
        assertEquals(Material.DIAMOND_HELMET, first.getInventory().getHelmet().getType());
        assertEquals(Material.DIAMOND_HELMET, second.getInventory().getHelmet().getType());
    }

    @Test
    void reportsWhenTheTeamHasNoKitAssigned() {
        PlayerMock member = server.addPlayer("Member");
        writeTeam("rot", null, List.of(member.getUniqueId().toString()));

        server.dispatchCommand(operator, "fr kit equip rot");

        assertEquals(
                PREFIX.append(Component.text("Team \"rot\" hat kein Kit zugewiesen.", NamedTextColor.RED)),
                operator.nextComponentMessage());
        assertNull(member.getInventory().getHelmet());
    }

    @Test
    void reportsWhenTheTeamsKitReferenceIsStale() {
        PlayerMock member = server.addPlayer("Member");
        saveHelmetKit("teamkit");
        writeTeam("rot", "teamkit", List.of(member.getUniqueId().toString()));
        server.dispatchCommand(operator, "fr kit delete teamkit");
        operator.nextComponentMessage();

        server.dispatchCommand(operator, "fr kit equip rot");

        assertEquals(
                PREFIX.append(Component.text("Kit \"teamkit\" wurde nicht gefunden.", NamedTextColor.RED)),
                operator.nextComponentMessage());
        assertNull(member.getInventory().getHelmet());
    }

    @Test
    void aTeamNameWinsOverAnEquallyNamedKit() {
        PlayerMock member = server.addPlayer("Member");
        saveHelmetKit("rot");
        saveHelmetKit("teamkit");
        writeTeam("rot", "teamkit", List.of(member.getUniqueId().toString()));

        server.dispatchCommand(operator, "fr kit equip rot");

        assertEquals(
                PREFIX.append(Component.text(
                        "Kit \"teamkit\" wurde an 1 Spieler aus Team \"rot\" ausgerüstet.", NamedTextColor.GREEN)),
                operator.nextComponentMessage());
        assertEquals(Material.DIAMOND_HELMET, member.getInventory().getHelmet().getType());
    }

    @Test
    void stillEquipsAKitOntoTheSenderWhenNoTeamMatches() {
        saveHelmetKit("solo");

        server.dispatchCommand(operator, "fr kit equip solo");

        assertEquals(
                PREFIX.append(Component.text("Kit \"solo\" wurde ausgerüstet.", NamedTextColor.GREEN)),
                operator.nextComponentMessage());
        assertEquals(Material.DIAMOND_HELMET, operator.getInventory().getHelmet().getType());
    }

    @Test
    void stillEquipsAnExplicitKitOntoATeam() {
        PlayerMock member = server.addPlayer("Member");
        saveHelmetKit("other");
        writeTeam("rot", null, List.of(member.getUniqueId().toString()));

        server.dispatchCommand(operator, "fr kit equip other rot");

        assertEquals(
                PREFIX.append(Component.text(
                        "Kit \"other\" wurde an 1 Spieler aus Team \"rot\" ausgerüstet.", NamedTextColor.GREEN)),
                operator.nextComponentMessage());
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
