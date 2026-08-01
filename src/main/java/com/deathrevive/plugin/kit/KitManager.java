package com.deathrevive.plugin.kit;

import org.bukkit.configuration.ConfigurationSection;
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
import java.util.Set;

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

    public void loadKits() {
        kitNames.clear();
        if (!kitsFile.exists()) {
            return;
        }

        try {
            kitsConfig.load(kitsFile);
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            plugin.getLogger().warning("Konnte kits.yml nicht laden: " + e.getMessage());
            return;
        }

        ConfigurationSection kitsSection = kitsConfig.getConfigurationSection("kits");
        if (kitsSection != null) {
            kitNames.addAll(kitsSection.getKeys(false));
        }
    }

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

    public Set<String> getKitNames() {
        return new HashSet<>(kitNames);
    }

    private void save() {
        try {
            plugin.getDataFolder().mkdirs();
            kitsConfig.save(kitsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte kits.yml nicht speichern: " + e.getMessage());
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
