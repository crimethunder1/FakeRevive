package com.fakerevive.kit;

import com.fakerevive.FakeRevivePlugin;
import com.fakerevive.support.PluginLoader;
import com.fakerevive.disguise.FakeIdentity;
import com.fakerevive.disguise.MojangIdentityFetcher;
import com.fakerevive.team.TeamManager;
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitManagerRobustnessTest {
    private ServerMock server;
    private FakeRevivePlugin plugin;
    private File kitsFile;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = PluginLoader.load();
        kitsFile = new File(plugin.getDataFolder(), "kits.yml");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void equipSkipsDefectiveItemsAppliesTheRestAndWarnsOncePerKit() throws IOException {
        KitManager kitManager = kitWithDefectiveEntries();
        List<String> warnings = captureWarnings();
        PlayerMock target = server.addPlayer("Target");

        assertTrue(kitManager.equipKit("mixed", target));
        assertTrue(kitManager.equipKit("mixed", target));

        assertEquals(Material.DIAMOND_SWORD, target.getInventory().getItem(0).getType());
        assertNull(target.getInventory().getItem(1));
        assertEquals(Material.DIAMOND_SWORD, target.getInventory().getItem(2).getType());
        assertEquals(Material.DIAMOND_HELMET, target.getInventory().getHelmet().getType());
        assertEquals(Material.SHIELD, target.getInventory().getItemInOffHand().getType());

        List<String> kitWarnings = warnings.stream().filter(message -> message.contains("\"mixed\"")).toList();
        assertEquals(1, kitWarnings.size());
        assertTrue(kitWarnings.get(0).contains("inventory 1"));
    }

    @Test
    void giveSkipsDefectiveItemsAndStillGivesTheRest() throws IOException {
        KitManager kitManager = kitWithDefectiveEntries();
        PlayerMock target = server.addPlayer("Receiver");

        assertTrue(kitManager.giveKit("mixed", target));

        assertTrue(target.getInventory().contains(Material.DIAMOND_SWORD));
        assertTrue(target.getInventory().contains(Material.DIAMOND_HELMET));
        assertTrue(target.getInventory().contains(Material.SHIELD));
        assertFalse(target.getInventory().contains(Material.APPLE));
    }

    @Test
    void brokenKitsFileIsBackedUpInsteadOfOverwritten() throws IOException {
        String brokenContent = "kits: [unclosed\n  - : :\n";
        Files.writeString(kitsFile.toPath(), brokenContent, StandardCharsets.UTF_8);
        KitManager kitManager = new KitManager(plugin);
        kitManager.loadKits();
        assertTrue(kitManager.getKitNames().isEmpty());

        PlayerMock player = server.addPlayer("Saver");
        player.getInventory().setItem(0, new ItemStack(Material.APPLE));
        kitManager.saveKit("fresh", player);

        File backup = new File(plugin.getDataFolder(), "kits.yml.broken");
        assertEquals(brokenContent, Files.readString(backup.toPath(), StandardCharsets.UTF_8));
        KitManager reloaded = new KitManager(plugin);
        reloaded.loadKits();
        assertEquals(Set.of("fresh"), reloaded.getKitNames());
    }

    @Test
    void saveKitRejectsInvalidNames() {
        KitManager kitManager = new KitManager(plugin);
        PlayerMock player = server.addPlayer("Saver");

        assertThrows(IllegalArgumentException.class, () -> kitManager.saveKit("with.dot", player));
        assertTrue(kitManager.getKitNames().isEmpty());
    }

    @Test
    void kitSaveCommandRejectsInvalidNamesAndStoresNothing() {
        PlayerMock admin = server.addPlayer("Admin");
        admin.setOp(true);

        server.dispatchCommand(admin, "fr kit save with.dot");
        server.dispatchCommand(admin, "fr kit save " + "a".repeat(33));

        assertFalse(kitsFile.exists() && YamlConfiguration.loadConfiguration(kitsFile).getConfigurationSection("kits") != null);
    }

    @Test
    void createTeamRejectsInvalidNames() {
        TeamManager teamManager = new TeamManager(plugin);
        teamManager.loadTeams();

        assertThrows(IllegalArgumentException.class, () -> teamManager.createTeam("red.team"));
        teamManager.createTeam("red_team-1");

        assertEquals(Set.of("red_team-1"), teamManager.getTeamNames());
    }

    @Test
    void brokenTeamsFileIsBackedUpInsteadOfOverwritten() throws IOException {
        File teamsFile = new File(plugin.getDataFolder(), "teams.yml");
        String brokenContent = "teams: {unclosed\n";
        Files.writeString(teamsFile.toPath(), brokenContent, StandardCharsets.UTF_8);
        TeamManager teamManager = new TeamManager(plugin);
        teamManager.loadTeams();

        teamManager.createTeam("fresh");

        File backup = new File(plugin.getDataFolder(), "teams.yml.broken");
        assertEquals(brokenContent, Files.readString(backup.toPath(), StandardCharsets.UTF_8));
        TeamManager reloaded = new TeamManager(plugin);
        reloaded.loadTeams();
        assertEquals(Set.of("fresh"), reloaded.getTeamNames());
    }

    private KitManager kitWithDefectiveEntries() throws IOException {
        PlayerMock source = server.addPlayer("Source");
        source.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));
        source.getInventory().setItem(1, new ItemStack(Material.APPLE));
        source.getInventory().setItem(2, new ItemStack(Material.DIAMOND_SWORD));
        source.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        source.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));

        KitManager writer = new KitManager(plugin);
        writer.loadKits();
        writer.saveKit("mixed", source);

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(kitsFile);
        List<String> inventory = new ArrayList<>(configuration.getStringList("kits.mixed.inventory"));
        inventory.set(1, "!!!not-base64!!!");
        configuration.set("kits.mixed.inventory", inventory);
        configuration.save(kitsFile);

        KitManager reader = new KitManager(plugin);
        reader.loadKits();
        return reader;
    }

    private List<String> captureWarnings() {
        List<String> warnings = new ArrayList<>();
        plugin.getLogger().addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    warnings.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return warnings;
    }
}
