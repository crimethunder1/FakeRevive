package com.fakerevive.team;

import com.fakerevive.kit.KitManager;
import com.fakerevive.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds and opens the FakeRevive team management GUIs.
 * Three screens: main menu (all teams), team detail (members + kit), kit picker.
 */
public class TeamGui {

    private static final DyeColor[] TEAM_COLORS = {
            DyeColor.RED, DyeColor.BLUE, DyeColor.GREEN, DyeColor.YELLOW,
            DyeColor.PURPLE, DyeColor.ORANGE, DyeColor.CYAN, DyeColor.PINK,
            DyeColor.LIME, DyeColor.MAGENTA, DyeColor.LIGHT_BLUE, DyeColor.BROWN
    };

    private final TeamManager teamManager;
    private final KitManager kitManager;
    private final MessageService messageService;

    public TeamGui(TeamManager teamManager, KitManager kitManager, MessageService messageService) {
        this.teamManager = teamManager;
        this.kitManager = kitManager;
        this.messageService = messageService;
    }

    /** Opens the main menu showing all teams as colored wool blocks plus a "create team" button. */
    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(
                new TeamGuiHolder("main", null), 54,
                Component.text(messageService.get("gui.team.main-title"), NamedTextColor.AQUA));

        fillBackground(inv);

        List<String> teamNames = new ArrayList<>(teamManager.getTeamNames());
        int[] innerSlots = innerSlots();
        for (int i = 0; i < Math.min(teamNames.size(), innerSlots.length); i++) {
            inv.setItem(innerSlots[i], buildTeamItem(teamNames.get(i)));
        }

        inv.setItem(49, makeGlass(Material.LIME_STAINED_GLASS_PANE,
                messageService.get("gui.team.create"),
                List.of(
                        Component.text(messageService.get("gui.team.lore-create"),
                                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                )));

        player.openInventory(inv);
    }

    /** Opens the detail view for a team: its members as player heads, its kit, and management buttons. */
    public void openTeamMenu(Player player, String teamName) {
        Inventory inv = Bukkit.createInventory(
                new TeamGuiHolder("team", teamName), 54,
                Component.text(messageService.get("gui.team.detail-title", "team", teamName), NamedTextColor.AQUA));

        fillBackground(inv);

        List<UUID> members = new ArrayList<>(teamManager.getTeamMembers(teamName));
        for (int i = 0; i < Math.min(members.size(), 28); i++) {
            inv.setItem(i, buildMemberHead(members.get(i)));
        }

        inv.setItem(45, makeGlass(Material.ARROW,
                messageService.get("gui.team.back"),
                List.of(Component.text(messageService.get("gui.team.lore-back"),
                        NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))));
        inv.setItem(46, makeGlass(Material.LIME_STAINED_GLASS_PANE,
                messageService.get("gui.team.add-player"),
                List.of(
                        Component.text(messageService.get("gui.team.lore-add-player"),
                                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text(messageService.get("gui.team.lore-add-player-hint"),
                                NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                )));
        inv.setItem(47, makeGlass(Material.ORANGE_STAINED_GLASS_PANE,
                messageService.get("gui.team.clear"),
                List.of(Component.text(messageService.get("gui.team.lore-clear"),
                        NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))));
        inv.setItem(48, buildKitDisplayItem(teamName));
        inv.setItem(50, makeGlass(Material.CHEST,
                messageService.get("gui.team.set-kit"),
                List.of(Component.text(messageService.get("gui.team.lore-set-kit"),
                        NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))));
        inv.setItem(53, makeGlass(Material.RED_STAINED_GLASS_PANE,
                messageService.get("gui.team.delete"),
                List.of(Component.text(messageService.get("gui.team.lore-delete"),
                        NamedTextColor.RED).decoration(TextDecoration.ITALIC, false))));

        player.openInventory(inv);
    }

    /** Opens the kit picker for a team, showing all saved kits as chest items. */
    public void openKitPicker(Player player, String teamName) {
        Inventory inv = Bukkit.createInventory(
                new TeamGuiHolder("kit", teamName), 54,
                Component.text(messageService.get("gui.kit.title"), NamedTextColor.AQUA));

        fillBackground(inv);

        List<String> kits = new ArrayList<>(kitManager.getKitNames());
        int[] innerSlots = innerSlots();
        for (int i = 0; i < Math.min(kits.size(), innerSlots.length); i++) {
            ItemStack kitItem = new ItemStack(Material.CHEST);
            ItemMeta meta = kitItem.getItemMeta();
            meta.displayName(Component.text(kits.get(i), NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text(messageService.get("gui.kit.lore-select"),
                    NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            kitItem.setItemMeta(meta);
            inv.setItem(innerSlots[i], kitItem);
        }

        inv.setItem(45, makeGlass(Material.ARROW, messageService.get("gui.team.back")));
        inv.setItem(49, makeGlass(Material.RED_STAINED_GLASS_PANE,
                messageService.get("gui.kit.remove"),
                List.of(Component.text(messageService.get("gui.kit.lore-remove"),
                        NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))));

        player.openInventory(inv);
    }

    private void fillBackground(Inventory inv) {
        ItemStack background = makeGlass(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, background);
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

    private ItemStack buildMemberHead(UUID uuid) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        meta.setOwningPlayer(offlinePlayer);

        String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : uuid.toString();
        boolean online = Bukkit.getPlayer(uuid) != null;
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

    /** @return the slots of the inner 4x7 area of a 6-row chest, skipping the border rows/columns. */
    static int[] innerSlots() {
        int[] slots = new int[28];
        int idx = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots[idx++] = row * 9 + col;
            }
        }
        return slots;
    }
}
