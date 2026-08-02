package com.fakerevive.team;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marks an inventory as a FakeRevive team GUI so the listener can identify
 * and cancel clicks without title-string comparisons.
 *
 * @param type     which screen this is: "main", "team", or "kit"
 * @param teamName the team this screen relates to (null for the main menu)
 */
public record TeamGuiHolder(String type, String teamName) implements InventoryHolder {
    @Override
    public Inventory getInventory() {
        return null;
    }
}
