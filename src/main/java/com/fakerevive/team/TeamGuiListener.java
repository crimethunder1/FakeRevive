package com.fakerevive.team;

import com.fakerevive.command.NearbyPlayerResolver;
import com.fakerevive.kit.KitManager;
import com.fakerevive.message.MessageService;
import com.fakerevive.storage.EntryNameValidator;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class TeamGuiListener implements Listener {
    private final JavaPlugin plugin;
    private final TeamManager teamManager;
    private final TeamGui teamGui;
    private final KitManager kitManager;
    private final MessageService messageService;

    private final Map<UUID, Consumer<String>> pendingPrompts = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public TeamGuiListener(JavaPlugin plugin, TeamManager teamManager, TeamGui teamGui,
                           KitManager kitManager, MessageService messageService) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.teamGui = teamGui;
        this.kitManager = kitManager;
        this.messageService = messageService;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TeamGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        switch (holder.type()) {
            case MAIN -> handleMainMenuClick(player, event.getSlot());
            case TEAM -> handleTeamMenuClick(player, holder.teamName(), event.getSlot());
            case KIT -> handleKitPickerClick(player, holder.teamName(), event.getSlot(), clicked);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof TeamGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        pendingPrompts.remove(event.getPlayer().getUniqueId());
    }

    boolean hasPendingPrompt(UUID playerId) {
        return pendingPrompts.containsKey(playerId);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncChat(AsyncChatEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Consumer<String> callback = pendingPrompts.remove(playerId);
        if (callback == null) {
            return;
        }

        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> callback.accept(input));
    }

    private void handleMainMenuClick(Player player, int slot) {
        if (slot == TeamGuiSlots.MAIN_CREATE_TEAM) {
            player.closeInventory();
            promptChat(player, "gui.team.type-team-name", rawInput -> {
                String input = rawInput.trim();
                if (input.equalsIgnoreCase("cancel")) {
                    messageService.send(player, "commands.team.cancelled", NamedTextColor.YELLOW);
                    return;
                }
                if (!EntryNameValidator.isValid(input)) {
                    messageService.send(player, "commands.team.invalid-name", NamedTextColor.RED, "team", input);
                    return;
                }
                if (teamManager.teamExists(input)) {
                    messageService.send(player, "commands.team.already-exists", NamedTextColor.RED, "team", input);
                    return;
                }
                teamManager.createTeam(input);
                messageService.send(player, "commands.team.created", NamedTextColor.GREEN, "team", input);
                Bukkit.getScheduler().runTask(plugin, () -> teamGui.openMainMenu(player));
            });
            return;
        }

        List<String> teamNames = new ArrayList<>(teamManager.getTeamNames());
        int[] innerSlots = TeamGuiSlots.innerSlots();
        for (int index = 0; index < Math.min(teamNames.size(), innerSlots.length); index++) {
            if (innerSlots[index] == slot) {
                teamGui.openTeamMenu(player, teamNames.get(index));
                return;
            }
        }
    }

    private void handleTeamMenuClick(Player player, String teamName, int slot) {
        switch (slot) {
            case TeamGuiSlots.TEAM_BACK -> teamGui.openMainMenu(player);
            case TeamGuiSlots.TEAM_ADD_PLAYER -> {
                player.closeInventory();
                promptChat(player, "gui.team.type-player-name", input -> {
                    String trimmed = input.trim();

                    if (trimmed.equalsIgnoreCase("cancel")) {
                        messageService.send(player, "commands.team.cancelled", NamedTextColor.YELLOW);
                        return;
                    }

                    if (trimmed.equalsIgnoreCase("@a")) {
                        int added = 0;
                        for (Player online : Bukkit.getOnlinePlayers()) {
                            teamManager.addPlayer(online.getUniqueId(), teamName);
                            added++;
                        }
                        messageService.send(player, "commands.team.players-added", NamedTextColor.GREEN,
                                "count", String.valueOf(added), "team", teamName);
                        Bukkit.getScheduler().runTask(plugin, () -> teamGui.openTeamMenu(player, teamName));
                        return;
                    }

                    if (NearbyPlayerResolver.isNearestSelector(trimmed)) {
                        Optional<Player> nearest = NearbyPlayerResolver.resolveNearest(player, candidate -> true);
                        if (nearest.isEmpty()) {
                            messageService.send(player, "commands.team.player-not-found", NamedTextColor.RED,
                                    "player", NearbyPlayerResolver.NEAREST_SELECTOR);
                            return;
                        }
                        teamManager.addPlayer(nearest.get().getUniqueId(), teamName);
                        messageService.send(player, "commands.team.player-added", NamedTextColor.GREEN,
                                "player", nearest.get().getName(), "team", teamName);
                        Bukkit.getScheduler().runTask(plugin, () -> teamGui.openTeamMenu(player, teamName));
                        return;
                    }

                    if (NearbyPlayerResolver.isNearSelector(trimmed)) {
                        int added = 0;
                        for (Player nearby : NearbyPlayerResolver.resolveNear(player, candidate -> true)) {
                            teamManager.addPlayer(nearby.getUniqueId(), teamName);
                            added++;
                        }
                        messageService.send(player, "commands.team.players-added", NamedTextColor.GREEN,
                                "count", String.valueOf(added), "team", teamName);
                        Bukkit.getScheduler().runTask(plugin, () -> teamGui.openTeamMenu(player, teamName));
                        return;
                    }

                    if (trimmed.equalsIgnoreCase("@r")) {
                        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
                        if (online.isEmpty()) {
                            messageService.send(player, "commands.team.player-not-found", NamedTextColor.RED,
                                    "player", "@r");
                            return;
                        }
                        Player randomPlayer = online.get(random.nextInt(online.size()));
                        teamManager.addPlayer(randomPlayer.getUniqueId(), teamName);
                        messageService.send(player, "commands.team.player-added", NamedTextColor.GREEN,
                                "player", randomPlayer.getName(), "team", teamName);
                        Bukkit.getScheduler().runTask(plugin, () -> teamGui.openTeamMenu(player, teamName));
                        return;
                    }

                    if (trimmed.equalsIgnoreCase("@split")) {
                        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
                        Collections.shuffle(online);
                        int half = online.size() / 2;
                        for (int index = 0; index < half; index++) {
                            teamManager.addPlayer(online.get(index).getUniqueId(), teamName);
                        }
                        messageService.send(player, "commands.team.players-added", NamedTextColor.GREEN,
                                "count", String.valueOf(half), "team", teamName);
                        Bukkit.getScheduler().runTask(plugin, () -> teamGui.openTeamMenu(player, teamName));
                        return;
                    }

                    String[] names = trimmed.split("\\s+");
                    int added = 0;
                    List<String> notFound = new ArrayList<>();

                    for (String name : names) {
                        if (name.isBlank()) continue;
                        Player target = Bukkit.getPlayer(name);
                        if (target == null) {
                            notFound.add(name);
                        } else {
                            teamManager.addPlayer(target.getUniqueId(), teamName);
                            added++;
                        }
                    }

                    if (added > 0) {
                        messageService.send(player, "commands.team.players-added", NamedTextColor.GREEN,
                                "count", String.valueOf(added), "team", teamName);
                    }
                    if (!notFound.isEmpty()) {
                        messageService.send(player, "commands.team.players-not-found", NamedTextColor.RED,
                                "players", String.join(", ", notFound));
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> teamGui.openTeamMenu(player, teamName));
                });
            }
            case TeamGuiSlots.TEAM_CLEAR -> {
                int cleared = 0;
                for (UUID memberId : teamManager.getTeamMembers(teamName)) {
                    Player member = Bukkit.getPlayer(memberId);
                    if (member == null) continue;
                    member.getInventory().clear();
                    member.getInventory().setArmorContents(null);
                    member.getInventory().setItemInOffHand(null);
                    member.updateInventory();
                    cleared++;
                }
                messageService.send(player, "commands.team.cleared", NamedTextColor.YELLOW,
                        "count", String.valueOf(cleared), "team", teamName);
                teamGui.openTeamMenu(player, teamName);
            }
            case TeamGuiSlots.TEAM_SET_KIT -> teamGui.openKitPicker(player, teamName);
            case TeamGuiSlots.TEAM_DELETE -> {
                teamManager.deleteTeam(teamName);
                messageService.send(player, "commands.team.deleted", NamedTextColor.GREEN, "team", teamName);
                teamGui.openMainMenu(player);
            }
            default -> handleMemberHeadClick(player, teamName, slot);
        }
    }

    private void handleMemberHeadClick(Player player, String teamName, int slot) {
        if (slot < TeamGuiSlots.MEMBER_FIRST || slot > TeamGuiSlots.MEMBER_LAST) {
            return;
        }
        List<UUID> members = new ArrayList<>(teamManager.getTeamMembers(teamName));
        if (slot >= members.size()) {
            return;
        }

        UUID memberId = members.get(slot);
        String memberName = Bukkit.getOfflinePlayer(memberId).getName();
        teamManager.removePlayer(memberId);
        messageService.send(player, "commands.team.player-removed", NamedTextColor.YELLOW,
                "player", memberName != null ? memberName : memberId.toString(), "team", teamName);
        teamGui.openTeamMenu(player, teamName);
    }

    private void handleKitPickerClick(Player player, String teamName, int slot, ItemStack clicked) {
        if (slot == TeamGuiSlots.KIT_BACK) {
            teamGui.openTeamMenu(player, teamName);
            return;
        }
        if (slot == TeamGuiSlots.KIT_REMOVE) {
            teamManager.setTeamKit(teamName, null);
            messageService.send(player, "commands.team.kit-removed", NamedTextColor.YELLOW, "team", teamName);
            teamGui.openTeamMenu(player, teamName);
            return;
        }
        if (clicked.getItemMeta() == null) {
            return;
        }

        String kitName = clicked.getItemMeta().getPersistentDataContainer()
                .get(teamGui.kitNameKey(), PersistentDataType.STRING);
        if (kitName == null || !kitManager.getKitNames().contains(kitName)) {
            return;
        }

        teamManager.setTeamKit(teamName, kitName);
        messageService.send(player, "commands.team.kit-set", NamedTextColor.GREEN, "kit", kitName, "team", teamName);
        teamGui.openTeamMenu(player, teamName);
    }

    private void promptChat(Player player, String promptKey, Consumer<String> callback) {
        pendingPrompts.put(player.getUniqueId(), callback);
        messageService.send(player, promptKey, NamedTextColor.YELLOW);
    }
}
