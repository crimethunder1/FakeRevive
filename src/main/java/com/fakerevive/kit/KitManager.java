package com.fakerevive.kit;

import com.fakerevive.storage.EntryNameValidator;
import com.fakerevive.storage.YamlFileStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KitManager {
    private static final int INVENTORY_SIZE = 36;
    private static final int ARMOR_SIZE = 4;

    private final JavaPlugin plugin;
    private final YamlFileStore store;
    private YamlConfiguration kitsConfig = new YamlConfiguration();
    private final Set<String> kitNames = new HashSet<>();
    private final Set<String> kitsWithReportedDefects = new HashSet<>();

    public KitManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.store = new YamlFileStore(plugin.getDataFolder().toPath().resolve("kits.yml"), plugin.getLogger());
    }

    public void loadKits() {
        kitNames.clear();
        kitsWithReportedDefects.clear();
        kitsConfig = store.load();

        ConfigurationSection kitsSection = kitsConfig.getConfigurationSection("kits");
        if (kitsSection != null) {
            kitNames.addAll(kitsSection.getKeys(false));
        }
    }

    public void saveKit(String name, Player player) {
        if (!EntryNameValidator.isValid(name)) {
            throw new IllegalArgumentException("Invalid kit name: " + name);
        }

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
        kitsWithReportedDefects.remove(name);
        store.save(kitsConfig);
    }

    public boolean equipKit(String name, Player player) {
        if (!kitNames.contains(name)) {
            return false;
        }

        String path = "kits." + name;
        List<String> failedSlots = new ArrayList<>();

        List<String> serializedInventory = kitsConfig.getStringList(path + ".inventory");
        ItemStack[] inventoryContents = new ItemStack[INVENTORY_SIZE];
        for (int slot = 0; slot < INVENTORY_SIZE && slot < serializedInventory.size(); slot++) {
            inventoryContents[slot] = deserialize(serializedInventory.get(slot), "inventory " + slot, failedSlots);
        }

        List<String> serializedArmor = kitsConfig.getStringList(path + ".armor");
        ItemStack[] armorContents = new ItemStack[ARMOR_SIZE];
        for (int slot = 0; slot < ARMOR_SIZE && slot < serializedArmor.size(); slot++) {
            armorContents[slot] = deserialize(serializedArmor.get(slot), "armor " + slot, failedSlots);
        }

        ItemStack offhandContents = deserialize(kitsConfig.getString(path + ".offhand", ""), "offhand", failedSlots);
        reportDefects(name, failedSlots);

        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            inventory.setItem(slot, inventoryContents[slot]);
        }
        inventory.setArmorContents(armorContents);
        inventory.setItemInOffHand(offhandContents);
        player.updateInventory();

        return true;
    }

    public boolean giveKit(String name, Player player) {
        if (!kitNames.contains(name)) {
            return false;
        }

        String path = "kits." + name;
        List<String> failedSlots = new ArrayList<>();
        List<ItemStack> items = new ArrayList<>();

        List<String> serializedInventory = kitsConfig.getStringList(path + ".inventory");
        for (int slot = 0; slot < serializedInventory.size(); slot++) {
            addIfPresent(items, deserialize(serializedInventory.get(slot), "inventory " + slot, failedSlots));
        }

        List<String> serializedArmor = kitsConfig.getStringList(path + ".armor");
        for (int slot = 0; slot < serializedArmor.size(); slot++) {
            addIfPresent(items, deserialize(serializedArmor.get(slot), "armor " + slot, failedSlots));
        }

        addIfPresent(items, deserialize(kitsConfig.getString(path + ".offhand", ""), "offhand", failedSlots));
        reportDefects(name, failedSlots);

        Map<Integer, ItemStack> overflow = player.getInventory().addItem(items.toArray(new ItemStack[0]));
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.updateInventory();
        return true;
    }

    public boolean deleteKit(String name) {
        if (!kitNames.contains(name)) {
            return false;
        }

        kitNames.remove(name);
        kitsWithReportedDefects.remove(name);
        kitsConfig.set("kits." + name, null);
        store.save(kitsConfig);
        return true;
    }

    public Set<String> getKitNames() {
        return new HashSet<>(kitNames);
    }

    private void addIfPresent(List<ItemStack> items, ItemStack item) {
        if (item != null) {
            items.add(item);
        }
    }

    private void reportDefects(String kitName, List<String> failedSlots) {
        if (failedSlots.isEmpty() || !kitsWithReportedDefects.add(kitName)) {
            return;
        }
        plugin.getLogger().warning("Kit \"" + kitName + "\" contains " + failedSlots.size()
                + " unreadable item(s) that were skipped. Affected slots: " + String.join(", ", failedSlots));
    }

    private String serialize(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "";
        }
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    private ItemStack deserialize(String data, String slotLabel, List<String> failedSlots) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
        } catch (RuntimeException exception) {
            failedSlots.add(slotLabel);
            return null;
        }
    }
}
