package com.fakerevive.team;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public record TeamGuiHolder(TeamGuiType type, String teamName) implements InventoryHolder {
    @Override
    public Inventory getInventory() {
        return null;
    }
}
