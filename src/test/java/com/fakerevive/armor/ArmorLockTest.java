package com.fakerevive.armor;

import com.fakerevive.FakeRevivePlugin;
import com.fakerevive.disguise.FakeIdentity;
import com.fakerevive.disguise.MojangIdentityFetcher;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmorLockTest {

    /** Raw slot of the helmet in a player's own inventory view. */
    private static final int HELMET_RAW_SLOT = 5;
    /** Raw slot of the first storage row, used as a "not an armor slot" control. */
    private static final int STORAGE_RAW_SLOT = 9;

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

    private PlayerMock lockedPlayerWearingHelmet(String name) {
        PlayerMock player = server.addPlayer(name);
        player.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        server.dispatchCommand(op, "fr armor on " + name);
        op.nextComponentMessage();
        return player;
    }

    /** Fires a real {@link InventoryClickEvent} through the plugin manager and reports the verdict. */
    private boolean clickIsBlocked(PlayerMock player, ClickType type, int rawSlot) {
        InventoryClickEvent event = new InventoryClickEvent(
                new PlayerInventoryViewStub(player),
                new PlayerInventoryViewStub(player).getSlotType(rawSlot),
                rawSlot,
                type,
                InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
        return event.isCancelled();
    }

    @Test
    void blocksTakingWornArmorOutOfTheArmorSlot() {
        PlayerMock player = lockedPlayerWearingHelmet("Locked");

        assertTrue(clickIsBlocked(player, ClickType.LEFT, HELMET_RAW_SLOT));
    }

    @Test
    void blocksShiftClickNumberKeyDropAndOffhandSwapOutOfTheArmorSlot() {
        PlayerMock player = lockedPlayerWearingHelmet("Locked");

        assertTrue(clickIsBlocked(player, ClickType.SHIFT_LEFT, HELMET_RAW_SLOT));
        assertTrue(clickIsBlocked(player, ClickType.NUMBER_KEY, HELMET_RAW_SLOT));
        assertTrue(clickIsBlocked(player, ClickType.DROP, HELMET_RAW_SLOT));
        assertTrue(clickIsBlocked(player, ClickType.CONTROL_DROP, HELMET_RAW_SLOT));
        assertTrue(clickIsBlocked(player, ClickType.SWAP_OFFHAND, HELMET_RAW_SLOT));
    }

    @Test
    void allowsFillingAnEmptyArmorSlotWhileLocked() {
        PlayerMock player = server.addPlayer("Locked");
        server.dispatchCommand(op, "fr armor on Locked");
        op.nextComponentMessage();

        // Nothing worn yet - the lock prevents removal, not equipping.
        assertFalse(clickIsBlocked(player, ClickType.LEFT, HELMET_RAW_SLOT));
    }

    @Test
    void allowsClicksOutsideTheArmorSlotsWhileLocked() {
        PlayerMock player = lockedPlayerWearingHelmet("Locked");

        assertFalse(clickIsBlocked(player, ClickType.LEFT, STORAGE_RAW_SLOT));
    }

    @Test
    void allowsUnlockedPlayerToRemoveArmor() {
        PlayerMock player = server.addPlayer("Free");
        player.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));

        assertFalse(clickIsBlocked(player, ClickType.LEFT, HELMET_RAW_SLOT));
    }

    @Test
    void stopsBlockingAfterArmorOff() {
        PlayerMock player = lockedPlayerWearingHelmet("Locked");
        server.dispatchCommand(op, "fr armor off Locked");
        op.nextComponentMessage();

        assertFalse(clickIsBlocked(player, ClickType.LEFT, HELMET_RAW_SLOT));
    }

    @Test
    void survivesAReloadFromDisk() {
        PlayerMock player = lockedPlayerWearingHelmet("Locked");
        server.dispatchCommand(op, "fr reload");
        op.nextComponentMessage();

        assertTrue(clickIsBlocked(player, ClickType.LEFT, HELMET_RAW_SLOT));
    }

    @Test
    void locksEveryTeamMemberIncludingOfflineOnes() {
        PlayerMock online = server.addPlayer("Online");
        PlayerMock offline = server.addPlayer("Offline");
        online.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        writeTeam("rot", null, List.of(online.getUniqueId().toString(), offline.getUniqueId().toString()));
        server.dispatchCommand(op, "fr reload");
        op.nextComponentMessage();

        server.dispatchCommand(op, "fr armor on rot");

        assertEquals(
                PREFIX.append(Component.text(
                        "Die Rüstung von 2 Spielern aus Team \"rot\" wurde gesperrt.", NamedTextColor.GREEN)),
                op.nextComponentMessage());
        assertTrue(clickIsBlocked(online, ClickType.LEFT, HELMET_RAW_SLOT));

        YamlConfiguration saved = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "armor-lock.yml"));
        assertTrue(saved.getStringList("locked").contains(offline.getUniqueId().toString()));
    }

    @Test
    void reportsStatusForASingleTarget() {
        lockedPlayerWearingHelmet("Locked");

        server.dispatchCommand(op, "fr armor status Locked");

        assertEquals(
                PREFIX.append(Component.text("Die Rüstung von Locked ist gesperrt.", NamedTextColor.YELLOW)),
                op.nextComponentMessage());
    }

    @Test
    void reportsEmptyStatusWhenNobodyIsLocked() {
        server.dispatchCommand(op, "fr armor status");

        assertEquals(
                PREFIX.append(Component.text("Kein Spieler hat aktuell eine Rüstungssperre.", NamedTextColor.YELLOW)),
                op.nextComponentMessage());
    }

    @Test
    void rejectsUnknownAction() {
        server.dispatchCommand(op, "fr armor sometimes");

        assertEquals(
                PREFIX.append(Component.text(
                        "Benutzung: /fr armor <on|off|status> [Spieler|Team|@a]", NamedTextColor.RED)),
                op.nextComponentMessage());
    }

    @Test
    void stubViewMapsArmorSlotsToTheWornPieces() {
        PlayerMock player = server.addPlayer("Wearer");
        player.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        player.getInventory().setBoots(new ItemStack(Material.IRON_BOOTS));
        PlayerInventoryViewStub view = new PlayerInventoryViewStub(player);

        // Guards the raw-slot layout the click tests above rely on.
        assertEquals(InventoryType.SlotType.ARMOR, view.getSlotType(HELMET_RAW_SLOT));
        assertEquals(Material.DIAMOND_HELMET, view.getItem(HELMET_RAW_SLOT).getType());
        assertEquals(Material.IRON_BOOTS, view.getItem(8).getType());
        assertEquals(InventoryType.SlotType.CONTAINER, view.getSlotType(STORAGE_RAW_SLOT));
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
    }
}
