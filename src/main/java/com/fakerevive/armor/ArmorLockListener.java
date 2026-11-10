package com.fakerevive.armor;

import com.fakerevive.message.MessageService;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ArmorLockListener implements Listener {
    private static final long NOTICE_COOLDOWN_MILLIS = 1500L;

    private final JavaPlugin plugin;
    private final ArmorLockManager armorLockManager;
    private final MessageService messageService;
    private final Map<UUID, Long> lastNotice = new HashMap<>();

    public ArmorLockListener(JavaPlugin plugin, ArmorLockManager armorLockManager,
                             MessageService messageService) {
        this.plugin = plugin;
        this.armorLockManager = armorLockManager;
        this.messageService = messageService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !armorLockManager.isLocked(player.getUniqueId())) {
            return;
        }

        if (!(event.getClickedInventory() instanceof PlayerInventory)
                || event.getSlotType() != InventoryType.SlotType.ARMOR) {
            return;
        }

        if (isEmpty(event.getCurrentItem())) {
            return;
        }

        event.setCancelled(true);
        notifyBlocked(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !armorLockManager.isLocked(player.getUniqueId())) {
            return;
        }

        for (int rawSlot : event.getRawSlots()) {
            if (event.getView().getSlotType(rawSlot) != InventoryType.SlotType.ARMOR) {
                continue;
            }
            if (isEmpty(event.getView().getItem(rawSlot))) {
                continue;
            }

            event.setCancelled(true);
            notifyBlocked(player);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (!armorLockManager.isLocked(player.getUniqueId())) {
            return;
        }

        ItemStack inHand = event.getItem();
        if (isEmpty(inHand)) {
            return;
        }

        EquipmentSlot targetSlot = armorSlotFor(inHand.getType());
        if (targetSlot == null || isEmpty(player.getInventory().getItem(targetSlot))) {
            return;
        }

        event.setCancelled(true);
        notifyBlocked(player);
    }

    private @Nullable EquipmentSlot armorSlotFor(Material material) {
        if (material == Material.ELYTRA) {
            return EquipmentSlot.CHEST;
        }
        if (material == Material.CARVED_PUMPKIN) {
            return EquipmentSlot.HEAD;
        }

        String name = material.name();
        if (name.endsWith("_HELMET") || name.endsWith("_HEAD") || name.endsWith("_SKULL")) {
            return EquipmentSlot.HEAD;
        }
        if (name.endsWith("_CHESTPLATE")) {
            return EquipmentSlot.CHEST;
        }
        if (name.endsWith("_LEGGINGS")) {
            return EquipmentSlot.LEGS;
        }
        if (name.endsWith("_BOOTS")) {
            return EquipmentSlot.FEET;
        }
        return null;
    }

    private void notifyBlocked(Player player) {
        if (!plugin.getConfig().getBoolean("armor.notify-blocked", true)) {
            return;
        }

        long now = System.currentTimeMillis();
        Long previous = lastNotice.get(player.getUniqueId());
        if (previous != null && now - previous < NOTICE_COOLDOWN_MILLIS) {
            return;
        }

        lastNotice.put(player.getUniqueId(), now);
        player.sendActionBar(messageService.prefixed("armor.locked-actionbar", NamedTextColor.RED));
    }

    private boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.getType().isAir();
    }
}
