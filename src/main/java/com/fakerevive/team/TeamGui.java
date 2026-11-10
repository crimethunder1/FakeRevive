package com.fakerevive.team;

import com.fakerevive.kit.KitManager;
import com.fakerevive.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TeamGui {
    private static final DyeColor[] TEAM_COLORS = {
            DyeColor.RED, DyeColor.BLUE, DyeColor.GREEN, DyeColor.YELLOW,
            DyeColor.PURPLE, DyeColor.ORANGE, DyeColor.CYAN, DyeColor.PINK,
            DyeColor.LIME, DyeColor.MAGENTA, DyeColor.LIGHT_BLUE, DyeColor.BROWN
    };

    private final TeamManager teamManager;
    private final KitManager kitManager;
    private final MessageService messageService;
    private final NamespacedKey kitNameKey;

    public TeamGui(JavaPlugin plugin, TeamManager teamManager, KitManager kitManager, MessageService messageService) {
        this.teamManager = teamManager;
        this.kitManager = kitManager;
        this.messageService = messageService;
        this.kitNameKey = new NamespacedKey(plugin, "kit_name");
    }

    NamespacedKey kitNameKey() {
        return kitNameKey;
    }

    public void openMainMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(
                new TeamGuiHolder(TeamGuiType.MAIN, null), TeamGuiSlots.INVENTORY_SIZE,
                Component.text(messageService.get("gui.team.main-title"), NamedTextColor.AQUA));

        fillBackground(inventory);

        List<String> teamNames = new ArrayList<>(teamManager.getTeamNames());
        int[] innerSlots = TeamGuiSlots.innerSlots();
        for (int index = 0; index < Math.min(teamNames.size(), innerSlots.length); index++) {
            inventory.setItem(innerSlots[index], buildTeamItem(teamNames.get(index)));
        }

        inventory.setItem(TeamGuiSlots.MAIN_CREATE_TEAM, makeGlass(Material.LIME_STAINED_GLASS_PANE,
                messageService.get("gui.team.create"),
                List.of(
                        Component.text(messageService.get("gui.team.lore-create"),
                                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                )));

        player.openInventory(inventory);
    }

    public void openTeamMenu(Player player, String teamName) {
        Inventory inventory = Bukkit.createInventory(
                new TeamGuiHolder(TeamGuiType.TEAM, teamName), TeamGuiSlots.INVENTORY_SIZE,
                Component.text(messageService.get("gui.team.detail-title", "team", teamName), NamedTextColor.AQUA));

        fillBackground(inventory);

        List<UUID> members = new ArrayList<>(teamManager.getTeamMembers(teamName));
        for (int index = 0; index < Math.min(members.size(), TeamGuiSlots.MEMBER_CAPACITY); index++) {
            inventory.setItem(TeamGuiSlots.MEMBER_FIRST + index, buildMemberHead(members.get(index)));
        }

        inventory.setItem(TeamGuiSlots.TEAM_BACK, makeGlass(Material.ARROW,
                messageService.get("gui.team.back"),
                List.of(Component.text(messageService.get("gui.team.lore-back"),
                        NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))));
        inventory.setItem(TeamGuiSlots.TEAM_ADD_PLAYER, makeGlass(Material.LIME_STAINED_GLASS_PANE,
                messageService.get("gui.team.add-player"),
                List.of(
                        Component.text(messageService.get("gui.team.lore-add-player"),
                                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text(messageService.get("gui.team.lore-add-player-hint"),
                                NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                )));
        inventory.setItem(TeamGuiSlots.TEAM_CLEAR, makeGlass(Material.ORANGE_STAINED_GLASS_PANE,
                messageService.get("gui.team.clear"),
                List.of(Component.text(messageService.get("gui.team.lore-clear"),
                        NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))));
        inventory.setItem(TeamGuiSlots.TEAM_KIT_DISPLAY, buildKitDisplayItem(teamName));
        inventory.setItem(TeamGuiSlots.TEAM_SET_KIT, makeGlass(Material.CHEST,
                messageService.get("gui.team.set-kit"),
                List.of(Component.text(messageService.get("gui.team.lore-set-kit"),
                        NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))));
        inventory.setItem(TeamGuiSlots.TEAM_DELETE, makeGlass(Material.RED_STAINED_GLASS_PANE,
                messageService.get("gui.team.delete"),
                List.of(Component.text(messageService.get("gui.team.lore-delete"),
                        NamedTextColor.RED).decoration(TextDecoration.ITALIC, false))));

        player.openInventory(inventory);
    }

    public void openKitPicker(Player player, String teamName) {
        Inventory inventory = Bukkit.createInventory(
                new TeamGuiHolder(TeamGuiType.KIT, teamName), TeamGuiSlots.INVENTORY_SIZE,
                Component.text(messageService.get("gui.kit.title"), NamedTextColor.AQUA));

        fillBackground(inventory);

        List<String> kitNames = new ArrayList<>(kitManager.getKitNames());
        int[] innerSlots = TeamGuiSlots.innerSlots();
        for (int index = 0; index < Math.min(kitNames.size(), innerSlots.length); index++) {
            ItemStack kitItem = new ItemStack(Material.CHEST);
            ItemMeta meta = kitItem.getItemMeta();
            meta.displayName(Component.text(kitNames.get(index), NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text(messageService.get("gui.kit.lore-select"),
                    NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(kitNameKey, PersistentDataType.STRING, kitNames.get(index));
            kitItem.setItemMeta(meta);
            inventory.setItem(innerSlots[index], kitItem);
        }

        inventory.setItem(TeamGuiSlots.KIT_BACK, makeGlass(Material.ARROW, messageService.get("gui.team.back")));
        inventory.setItem(TeamGuiSlots.KIT_REMOVE, makeGlass(Material.RED_STAINED_GLASS_PANE,
                messageService.get("gui.kit.remove"),
                List.of(Component.text(messageService.get("gui.kit.lore-remove"),
                        NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))));

        player.openInventory(inventory);
    }

    private void fillBackground(Inventory inventory) {
        ItemStack background = makeGlass(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, background);
        }
    }

    private ItemStack buildTeamItem(String name) {
        Material wool = Material.valueOf(teamColor(name).name() + "_WOOL");
        ItemStack item = new ItemStack(wool);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));

        String kit = teamManager.getTeamKit(name).orElse(null);
        int memberCount = teamManager.getTeamMembers(name).size();
        meta.lore(List.of(
                Component.text(messageService.get("gui.team.lore-kit",
                                "kit", kit != null ? kit : messageService.get("gui.team.no-kit")),
                        NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text(messageService.get("gui.team.lore-members", "count", String.valueOf(memberCount)),
                        NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text(messageService.get("gui.team.lore-click"),
                        NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildMemberHead(UUID uniqueId) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uniqueId);
        meta.setOwningPlayer(offlinePlayer);

        String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : uniqueId.toString();
        boolean online = Bukkit.getPlayer(uniqueId) != null;
        meta.displayName(Component.text(name, online ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(messageService.get("gui.team.member-click-remove"),
                        NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
        ));
        skull.setItemMeta(meta);
        return skull;
    }

    private ItemStack buildKitDisplayItem(String teamName) {
        String kit = teamManager.getTeamKit(teamName).orElse(null);
        ItemStack item = new ItemStack(kit != null ? Material.BOOK : Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(
                messageService.get("gui.team.current-kit", "kit", kit != null ? kit : messageService.get("gui.team.no-kit")),
                NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(messageService.get("gui.team.lore-current-kit"),
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeGlass(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeGlass(Material material, String name, List<Component> lore) {
        ItemStack item = makeGlass(material, name);
        ItemMeta meta = item.getItemMeta();
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private DyeColor teamColor(String name) {
        return TEAM_COLORS[Math.abs(name.hashCode()) % TEAM_COLORS.length];
    }
}
