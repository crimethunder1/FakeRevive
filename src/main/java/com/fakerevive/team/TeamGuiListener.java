package com.fakerevive.team;

import com.fakerevive.kit.KitManager;
import com.fakerevive.message.MessageService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Handles all click events inside FakeRevive team GUIs and manages one-shot chat
 * prompts (for team creation and player addition).
 */
public class TeamGuiListener implements Listener {

    private final JavaPlugin plugin;
    private final TeamManager teamManager;
    private final TeamGui teamGui;
    private final KitManager kitManager;
    private final MessageService messageService;
    private final Component prefix;

    // Players currently waiting to type a response in chat, keyed by UUID.
    private final Map<UUID, Consumer<String>> pendingPrompts = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public TeamGuiListener(JavaPlugin plugin, TeamManager teamManager, TeamGui teamGui,
                           KitManager kitManager, MessageService messageService, Component prefix) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.teamGui = teamGui;
        this.kitManager = kitManager;
        this.messageService = messageService;
        this.prefix = prefix;
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
            case "main" -> handleMainMenuClick(player, event.getSlot());
            case "team" -> handleTeamMenuClick(player, holder.teamName(), event.getSlot());
            case "kit" -> handleKitPickerClick(player, holder.teamName(), event.getSlot(), clicked);
        }
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
        if (slot == 49) {
            player.closeInventory();
            promptChat(player, messageService.get("gui.team.type-team-name"), input -> {
                if (input.equalsIgnoreCase("cancel")) {
                    sendMessage(player, "commands.team.cancelled", NamedTextColor.YELLOW);
                    return;
                }
                if (teamManager.teamExists(input)) {
                    sendMessage(player, "commands.team.already-exists", NamedTextColor.RED, "team", input);
                    return;
                }
                teamManager.createTeam(input);
                sendMessage(player, "commands.team.created", NamedTextColor.GREEN, "team", input);
                Bukkit.getScheduler().runTask(plugin, () -> teamGui.openMainMenu(player));
            });
            return;
        }

        List<String> teamNames = new ArrayList<>(teamManager.getTeamNames());
        int[] innerSlots = TeamGui.innerSlots();
        for (int i = 0; i < Math.min(teamNames.size(), innerSlots.length); i++) {
            if (innerSlots[i] == slot) {
                teamGui.openTeamMenu(player, teamNames.get(i));
                return;
            }
        }
    }

    private void handleTeamMenuClick(Player player, String teamName, int slot) {
        switch (slot) {
            case 45 -> teamGui.openMainMenu(player);
            case 46 -> {
                player.closeInventory();
                promptChat(player, messageService.get("gui.team.type-player-name"), input -> {
                    String trimmed = input.trim();

                    if (trimmed.equalsIgnoreCase("cancel")) {
                        sendMessage(player, "commands.team.cancelled", NamedTextColor.YELLOW);
                        return;
                    }

                    if (trimmed.equalsIgnoreCase("@a")) {
                        int added = 0;
                        for (Player online : Bukkit.getOnlinePlayers()) {
                            teamManager.addPlayer(online.getUniqueId(), teamName);
                            added++;
                        }
                        sendMessage(player, "commands.team.players-added", NamedTextColor.GREEN,
                                "count", String.valueOf(added), "team", teamName);
                        Bukkit.getScheduler().runTask(plugin, () -> teamGui.openTeamMenu(player, teamName));
                        return;
                    }

                    if (trimmed.equalsIgnoreCase("@p")) {
                        int added = 0;
                        for (Player nearby : player.getWorld().getPlayers()) {
                            if (nearby.getLocation().distanceSquared(player.getLocation()) <= 16 * 16) {
                                teamManager.addPlayer(nearby.getUniqueId(), teamName);
                                added++;
                            }
                        }
                        sendMessage(player, "commands.team.players-added", NamedTextColor.GREEN,
                                "count", String.valueOf(added), "team", teamName);
                        Bukkit.getScheduler().runTask(plugin, () -> teamGui.openTeamMenu(player, teamName));
                        return;
                    }

                    if (trimmed.equalsIgnoreCase("@r")) {
                        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
                        if (online.isEmpty()) {
                            sendMessage(player, "commands.team.player-not-found", NamedTextColor.RED, "player", "@r");
                            return;
                        }
                        Player randomPlayer = online.get(random.nextInt(online.size()));
                        teamManager.addPlayer(randomPlayer.getUniqueId(), teamName);
                        sendMessage(player, "commands.team.player-added", NamedTextColor.GREEN,
                                "player", randomPlayer.getName(), "team", teamName);
                        Bukkit.getScheduler().runTask(plugin, () -> teamGui.openTeamMenu(player, teamName));
                        return;
                    }

                    if (trimmed.equalsIgnoreCase("@split")) {
                        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
                        Collections.shuffle(online);
                        int half = online.size() / 2;
                        for (int i = 0; i < half; i++) {
                            teamManager.addPlayer(online.get(i).getUniqueId(), teamName);
                        }
                        sendMessage(player, "commands.team.players-added", NamedTextColor.GREEN,
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
                        sendMessage(player, "commands.team.players-added", NamedTextColor.GREEN,
                                "count", String.valueOf(added), "team", teamName);
                    }
                    if (!notFound.isEmpty()) {
                        sendMessage(player, "commands.team.players-not-found", NamedTextColor.RED,
                                "players", String.join(", ", notFound));
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> teamGui.openTeamMenu(player, teamName));
                });
            }
            case 50 -> teamGui.openKitPicker(player, teamName);
            case 53 -> {
                teamManager.deleteTeam(teamName);
                sendMessage(player, "commands.team.deleted", NamedTextColor.GREEN, "team", teamName);
                teamGui.openMainMenu(player);
            }
            default -> handleMemberHeadClick(player, teamName, slot);
        }
    }

    private void handleMemberHeadClick(Player player, String teamName, int slot) {
        if (slot < 0 || slot > 27) {
            return;
        }
        List<UUID> members = new ArrayList<>(teamManager.getTeamMembers(teamName));
        if (slot >= members.size()) {
            return;
        }

        UUID memberId = members.get(slot);
        String memberName = Bukkit.getOfflinePlayer(memberId).getName();
        teamManager.removePlayer(memberId);
        sendMessage(player, "commands.team.player-removed", NamedTextColor.YELLOW,
                "player", memberName != null ? memberName : memberId.toString(), "team", teamName);
        teamGui.openTeamMenu(player, teamName);
    }

    private void handleKitPickerClick(Player player, String teamName, int slot, ItemStack clicked) {
        if (slot == 45) {
            teamGui.openTeamMenu(player, teamName);
            return;
        }
        if (slot == 49) {
            teamManager.setTeamKit(teamName, null);
            sendMessage(player, "commands.team.kit-removed", NamedTextColor.YELLOW, "team", teamName);
            teamGui.openTeamMenu(player, teamName);
            return;
        }
        if (clicked.getItemMeta() == null || !clicked.getItemMeta().hasDisplayName()) {
            return;
        }

        String kitName = PlainTextComponentSerializer.plainText().serialize(clicked.getItemMeta().displayName());
        if (!kitManager.getKitNames().contains(kitName)) {
            return;
        }

        teamManager.setTeamKit(teamName, kitName);
        sendMessage(player, "commands.team.kit-set", NamedTextColor.GREEN, "kit", kitName, "team", teamName);
        teamGui.openTeamMenu(player, teamName);
    }

    /**
     * Intercepts the player's next chat message, cancels it, and runs the callback on the
     * main thread. The pending entry is consumed regardless of what the player types.
     */
    private void promptChat(Player player, String prompt, Consumer<String> callback) {
        pendingPrompts.put(player.getUniqueId(), callback);
        player.sendMessage(prefix.append(Component.text(prompt, NamedTextColor.YELLOW)));
    }

    private void sendMessage(Player player, String key, NamedTextColor color, String... placeholders) {
        player.sendMessage(prefix.append(Component.text(messageService.get(key, placeholders), color)));
    }
}
