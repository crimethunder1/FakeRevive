package com.fakerevive.team;

import com.fakerevive.FakeRevivePlugin;
import com.fakerevive.support.PluginLoader;
import com.fakerevive.disguise.FakeIdentity;
import com.fakerevive.disguise.MojangIdentityFetcher;
import com.fakerevive.kit.KitManager;
import com.fakerevive.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamGuiListenerTest {
    private static final int FIRST_INNER_SLOT = 10;
    private static final int SECOND_INNER_SLOT = 11;

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

    private void saveHelmetKit(String kitName) {
        operator.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        server.dispatchCommand(operator, "fr kit save " + kitName);
        operator.nextComponentMessage();
        operator.getInventory().setHelmet(null);
    }

    private void click(int slot) {
        InventoryView view = operator.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private ItemStack renamedChest(String name) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW));
        item.setItemMeta(meta);
        return item;
    }

    @Test
    void cancelsDraggingItemsIntoTheTeamGui() {
        writeTeam("rot", null, List.of());
        server.dispatchCommand(operator, "fr team");
        InventoryView view = operator.getOpenInventory();
        assertInstanceOf(TeamGuiHolder.class, view.getTopInventory().getHolder());

        InventoryDragEvent event = new InventoryDragEvent(
                view, new ItemStack(Material.AIR), new ItemStack(Material.DIRT), false,
                Map.of(SECOND_INNER_SLOT, new ItemStack(Material.DIRT)));
        server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void picksTheKitThroughThePersistentDataOfTheClickedItem() {
        saveHelmetKit("teamkit");
        writeTeam("rot", null, List.of(operator.getUniqueId().toString()));

        server.dispatchCommand(operator, "fr team");
        click(FIRST_INNER_SLOT);
        assertEquals(TeamGuiType.TEAM, ((TeamGuiHolder) operator.getOpenInventory().getTopInventory().getHolder()).type());
        click(TeamGuiSlots.TEAM_SET_KIT);
        assertEquals(TeamGuiType.KIT, ((TeamGuiHolder) operator.getOpenInventory().getTopInventory().getHolder()).type());
        click(FIRST_INNER_SLOT);

        assertEquals(TeamGuiType.TEAM, ((TeamGuiHolder) operator.getOpenInventory().getTopInventory().getHolder()).type());
        server.dispatchCommand(operator, "fr kit equip rot");
        assertNotNull(operator.getInventory().getHelmet());
        assertEquals(Material.DIAMOND_HELMET, operator.getInventory().getHelmet().getType());
    }

    @Test
    void ignoresAnItemThatOnlyCopiesTheKitDisplayName() {
        saveHelmetKit("teamkit");
        writeTeam("rot", null, List.of(operator.getUniqueId().toString()));

        server.dispatchCommand(operator, "fr team");
        click(FIRST_INNER_SLOT);
        click(TeamGuiSlots.TEAM_SET_KIT);
        operator.getOpenInventory().getTopInventory().setItem(SECOND_INNER_SLOT, renamedChest("teamkit"));
        click(SECOND_INNER_SLOT);

        assertEquals(TeamGuiType.KIT, ((TeamGuiHolder) operator.getOpenInventory().getTopInventory().getHolder()).type());
        server.dispatchCommand(operator, "fr kit equip rot");
        assertNull(operator.getInventory().getHelmet());
    }

    @Test
    void storesTheKitNameInThePersistentDataOfEachPickerItem() {
        saveHelmetKit("teamkit");
        writeTeam("rot", null, List.of());

        server.dispatchCommand(operator, "fr team");
        click(FIRST_INNER_SLOT);
        click(TeamGuiSlots.TEAM_SET_KIT);

        ItemStack kitItem = operator.getOpenInventory().getTopInventory().getItem(FIRST_INNER_SLOT);
        assertNotNull(kitItem);
        assertEquals(Material.CHEST, kitItem.getType());
        String storedName = kitItem.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(plugin, "kit_name"), PersistentDataType.STRING);
        assertEquals("teamkit", storedName);
    }

    @Test
    void removesThePendingPromptWhenThePlayerQuits() {
        MessageService messageService = new MessageService(plugin, "en");
        TeamManager teamManager = new TeamManager(plugin);
        KitManager kitManager = new KitManager(plugin);
        TeamGui teamGui = new TeamGui(plugin, teamManager, kitManager, messageService);
        TeamGuiListener listener = new TeamGuiListener(
                plugin, teamManager, teamGui, kitManager, messageService);

        teamGui.openMainMenu(operator);
        InventoryClickEvent createClick = new InventoryClickEvent(
                operator.getOpenInventory(), InventoryType.SlotType.CONTAINER, TeamGuiSlots.MAIN_CREATE_TEAM,
                ClickType.LEFT, InventoryAction.PICKUP_ALL);
        listener.onInventoryClick(createClick);
        assertTrue(listener.hasPendingPrompt(operator.getUniqueId()));

        listener.onPlayerQuit(new PlayerQuitEvent(
                operator, Component.text("Admin left the game"), PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertFalse(listener.hasPendingPrompt(operator.getUniqueId()));
    }
}
