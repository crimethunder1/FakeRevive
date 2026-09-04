package com.fakerevive.armor;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.mockbukkit.mockbukkit.inventory.InventoryViewMock;

/**
 * MockBukkit's {@code PlayerInventoryViewMock} leaves {@code getSlotType} and {@code getInventory}
 * unimplemented, which makes any {@code InventoryClickEvent} against it throw - and JUnit reports
 * that as a <em>skipped</em> test rather than a failure. This stub fills in the raw-slot layout of
 * a player's own inventory view so armor-slot clicks can actually be exercised:
 *
 * <pre>
 *   0        crafting result
 *   1 -  4   crafting grid
 *   5 -  8   armor      (helmet, chestplate, leggings, boots)
 *   9 - 35   storage
 *  36 - 44   hotbar
 *  45        offhand
 * </pre>
 */
final class PlayerInventoryViewStub extends InventoryViewMock {

    private static final int FIRST_ARMOR_RAW_SLOT = 5;
    private static final int LAST_ARMOR_RAW_SLOT = 8;
    private static final int OFFHAND_RAW_SLOT = 45;

    PlayerInventoryViewStub(Player player) {
        super(player, Bukkit.createInventory(null, 9), player.getInventory(), InventoryType.CRAFTING);
    }

    @Override
    public Inventory getInventory(int rawSlot) {
        return rawSlot < FIRST_ARMOR_RAW_SLOT ? getTopInventory() : getBottomInventory();
    }

    @Override
    public int convertSlot(int rawSlot) {
        if (rawSlot < FIRST_ARMOR_RAW_SLOT) {
            return rawSlot;
        }
        if (rawSlot <= LAST_ARMOR_RAW_SLOT) {
            // Raw order is helmet-first, PlayerInventory order is boots-first.
            return 39 - (rawSlot - FIRST_ARMOR_RAW_SLOT);
        }
        if (rawSlot == OFFHAND_RAW_SLOT) {
            return 40;
        }
        if (rawSlot <= 35) {
            return rawSlot;
        }
        return rawSlot - 36;
    }

    @Override
    public ItemStack getItem(int rawSlot) {
        return getInventory(rawSlot).getItem(convertSlot(rawSlot));
    }

    @Override
    public void setItem(int rawSlot, ItemStack item) {
        getInventory(rawSlot).setItem(convertSlot(rawSlot), item);
    }

    @Override
    public InventoryType.SlotType getSlotType(int rawSlot) {
        if (rawSlot == 0) {
            return InventoryType.SlotType.RESULT;
        }
        if (rawSlot < FIRST_ARMOR_RAW_SLOT) {
            return InventoryType.SlotType.CRAFTING;
        }
        if (rawSlot <= LAST_ARMOR_RAW_SLOT) {
            return InventoryType.SlotType.ARMOR;
        }
        if (rawSlot <= 35) {
            return InventoryType.SlotType.CONTAINER;
        }
        return InventoryType.SlotType.QUICKBAR;
    }

    @Override
    public int countSlots() {
        return 46;
    }

    @Override
    public void open() {
        // The view is only ever used to carry slot layout into an InventoryClickEvent.
    }

    @Override
    public void close() {
        // Nothing to tear down.
    }
}
