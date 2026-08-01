package com.fakerevive.kit;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages named server kits — snapshots of a player's inventory, armor slots, and offhand.
 * Kits are stored in {@code kits.yml} inside the plugin's data folder. Items are serialised
 * as Base64-encoded byte arrays via Paper's {@link ItemStack#serializeAsBytes()}.
 */
public class KitManager {

    private static final int INVENTORY_SIZE = 36;
    private static final int ARMOR_SIZE = 4;

    private final JavaPlugin plugin;
    private final File kitsFile;
    private final YamlConfiguration kitsConfig = new YamlConfiguration();
    private final Set<String> kitNames = new HashSet<>();

    public KitManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.kitsFile = new File(plugin.getDataFolder(), "kits.yml");
    }

    /**
     * Loads kit names from {@code kits.yml}. Safe to call multiple times; clears the
     * in-memory kit list before reloading. A missing file is treated as an empty kit list.
     */
    public void loadKits() {
        kitNames.clear();
        if (!kitsFile.exists()) {
            return;
        }

        try {
            kitsConfig.load(kitsFile);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning("Failed to load kits.yml: " + e.getMessage());
            return;
        }

        ConfigurationSection kitsSection = kitsConfig.getConfigurationSection("kits");
        if (kitsSection != null) {
            kitNames.addAll(kitsSection.getKeys(false));
        }
    }

    /**
     * Captures the player's current inventory, armor, and offhand as a named kit and
     * persists it to {@code kits.yml}, overwriting any existing kit with the same name.
     */
    public void saveKit(String name, Player player) {
        PlayerInventory inventory = player.getInventory();

        List<String> serializedInventory = new ArrayList<>(INVENTORY_SIZE);
        for (ItemStack item : inventory.getStorageContents()) {
            serializedInventory.add(serialize(item));
        }

        List<String> serializedArmor = new ArrayList<>(ARMOR_SIZE);
        for (ItemStack item : inventory.getArmorContents()) {
            serializedArmor.add(serialize(item));
        }

        String path = "kits." + name;
        kitsConfig.set(path + ".inventory", serializedInventory);
        kitsConfig.set(path + ".armor", serializedArmor);
        kitsConfig.set(path + ".offhand", serialize(inventory.getItemInOffHand()));

        kitNames.add(name);
        save();
    }

    /**
     * Equips the named kit onto the player by setting inventory, armor, and offhand contents
     * directly. Items are placed into the correct slots regardless of inventory capacity —
     * nothing is dropped.
     * @return {@code true} if the kit was found and applied; {@code false} if no kit with
     *     that name exists.
     */
    public boolean equipKit(String name, Player player) {
        if (!kitNames.contains(name)) {
            return false;
        }

        String path = "kits." + name;
        List<String> serializedInventory = kitsConfig.getStringList(path + ".inventory");
        List<String> serializedArmor = kitsConfig.getStringList(path + ".armor");
        String serializedOffhand = kitsConfig.getString(path + ".offhand", "");

        ItemStack[] inventoryContents = new ItemStack[INVENTORY_SIZE];
        for (int i = 0; i < INVENTORY_SIZE && i < serializedInventory.size(); i++) {
            inventoryContents[i] = deserialize(serializedInventory.get(i));
        }

        ItemStack[] armorContents = new ItemStack[ARMOR_SIZE];
        for (int i = 0; i < ARMOR_SIZE && i < serializedArmor.size(); i++) {
            armorContents[i] = deserialize(serializedArmor.get(i));
        }

        PlayerInventory inventory = player.getInventory();
        inventory.setStorageContents(inventoryContents);
        inventory.setArmorContents(armorContents);
        inventory.setItemInOffHand(deserialize(serializedOffhand));
        player.updateInventory();

        return true;
    }

    /**
     * Adds the named kit's inventory, armor, and offhand contents to the player using
     * addItem(), so items that don't fit overflow onto the ground at the player's feet
     * instead of being lost.
     *
     * @return {@code true} if the kit was found; {@code false} otherwise.
     */
    public boolean giveKit(String name, Player player) {
        if (!kitNames.contains(name)) {
            return false;
        }

        String path = "kits." + name;
        List<ItemStack> items = new ArrayList<>();

        for (String entry : kitsConfig.getStringList(path + ".inventory")) {
            ItemStack item = deserialize(entry);
            if (item != null) {
                items.add(item);
            }
        }

        for (String entry : kitsConfig.getStringList(path + ".armor")) {
            ItemStack item = deserialize(entry);
            if (item != null) {
                items.add(item);
            }
        }

        ItemStack offhand = deserialize(kitsConfig.getString(path + ".offhand", ""));
        if (offhand != null) {
            items.add(offhand);
        }

        Map<Integer, ItemStack> overflow = player.getInventory().addItem(items.toArray(new ItemStack[0]));
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.updateInventory();
        return true;
    }

    /** @return a snapshot of all currently registered kit names; modifications do not affect the registry. */
    public Set<String> getKitNames() {
        return new HashSet<>(kitNames);
    }

    private void save() {
        try {
            plugin.getDataFolder().mkdirs();
            kitsConfig.save(kitsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save kits.yml: " + e.getMessage());
        }
    }

    private String serialize(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "";
        }
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    private ItemStack deserialize(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
    }
}
