package com.fakerevive.armor;

import com.fakerevive.message.MessageService;
import net.kyori.adventure.text.Component;
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

/**
 * Enforces the armor locks tracked by {@link ArmorLockManager}. Behaves like Curse of Binding
 * without enchanting the items: a locked player can still <em>put on</em> armor, but every way of
 * taking a worn piece back off is cancelled. Death is deliberately left alone, so locked armor
 * drops as usual.
 */
public class ArmorLockListener implements Listener {

    private static final long NOTICE_COOLDOWN_MILLIS = 1500L;

    private final JavaPlugin plugin;
    private final ArmorLockManager armorLockManager;
    private final MessageService messageService;
    private final Component prefix;
    private final Map<UUID, Long> lastNotice = new HashMap<>();

    public ArmorLockListener(JavaPlugin plugin, ArmorLockManager armorLockManager,
                             MessageService messageService, Component prefix) {
        this.plugin = plugin;
        this.armorLockManager = armorLockManager;
        this.messageService = messageService;
        this.prefix = prefix;
    }

    /**
     * Covers every click that can pull a piece out of an armor slot: normal and shift clicks,
     * hotbar number swaps, offhand swaps, and Q/Ctrl+Q drops. All of them report the armor slot
     * as the clicked slot, so a single slot-type check catches them.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !armorLockManager.isLocked(player.getUniqueId())) {
            return;
        }

        if (!(event.getClickedInventory() instanceof PlayerInventory)
                || event.getSlotType() != InventoryType.SlotType.ARMOR) {
            return;
        }

        // An empty armor slot is fair game - locking prevents removal, not equipping.
        if (isEmpty(event.getCurrentItem())) {
            return;
        }

        event.setCancelled(true);
        notifyBlocked(player);
    }

    /** Dragging across an occupied armor slot can swap the worn piece out, so block it too. */
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

    /**
     * The one removal path {@link #onInventoryClick} can't see: right-clicking with an armor piece
     * in hand swaps out whatever is already worn in that slot (the classic elytra/chestplate swap).
     */
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

    /**
     * Maps an item to the armor slot it would be worn in, or {@code null} if it isn't wearable.
     * Uses the material name suffix so new armor tiers are picked up automatically.
     */
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

    /**
     * Sends the "armor is locked" action bar, throttled per player so that holding shift-click
     * doesn't flood the bar with restarts of the same message.
     */
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
        player.sendActionBar(prefix.append(Component.text(
                messageService.get("armor.locked-actionbar"), NamedTextColor.RED)));
    }

    private boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.getType().isAir();
    }
}
