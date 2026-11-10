package com.fakerevive.listener;

import com.fakerevive.FakeRevivePlugin;
import com.fakerevive.disguise.FakeIdentity;
import com.fakerevive.disguise.PlayerDisguiseService;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.advancement.Advancement;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Proxy;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeLeaveListenerTest {
    private ServerMock server;
    private FakeRevivePlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(FakeRevivePlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void deathMarksPlayerAsFakedOutBeforeTheRespawnTaskRuns() {
        PlayerMock player = server.addPlayer("Victim");

        player.damage(player.getHealth() + 1);

        PlayerQuitEvent quitEvent = new PlayerQuitEvent(player, Component.text("Victim left the game"),
                PlayerQuitEvent.QuitReason.DISCONNECTED);
        Bukkit.getPluginManager().callEvent(quitEvent);

        assertNull(quitEvent.quitMessage());
    }

    @Test
    void respawnTaskIsCancelledWhenPlayerDisconnectsBeforeItFires() {
        PlayerMock player = server.addPlayer("Victim");
        player.damage(player.getHealth() + 1);

        player.disconnect();

        assertDoesNotThrow(() -> server.getScheduler().performTicks(2L));
    }

    @Test
    void quitMessageIsUnaffectedForPlayerInSpectatorModeWithoutFakeLeave() {
        PlayerMock bystander = server.addPlayer("Bystander");
        bystander.setGameMode(GameMode.SPECTATOR);

        PlayerQuitEvent quitEvent = new PlayerQuitEvent(bystander, Component.text("Bystander left the game"),
                PlayerQuitEvent.QuitReason.DISCONNECTED);
        Bukkit.getPluginManager().callEvent(quitEvent);

        assertNotNull(quitEvent.quitMessage());
    }

    @Test
    void usesFakeNameInsteadOfRealNameInBroadcastWhileDisguiseIsActive() {
        PlayerMock target = server.addPlayer("Target");
        PlayerMock bystander = server.addPlayer("Bystander");
        plugin.getActiveDisguiseRegistry().assign(target.getUniqueId(),
                new FakeIdentity("Crimson_Wolf", "skin-value", "skin-signature"));
        drainMessages(bystander);

        target.damage(target.getHealth() + 1);

        assertEquals(
                "Crimson_Wolf got killed",
                PlainTextComponentSerializer.plainText().serialize(bystander.nextComponentMessage()));
        assertEquals(
                Component.text("Crimson_Wolf left the game", NamedTextColor.YELLOW),
                bystander.nextComponentMessage());
        assertTrue(plugin.getActiveDisguiseRegistry().getFakeName(target.getUniqueId()).isEmpty());
    }

    @Test
    void playerWithSeeRealPermissionButWithoutOpSeesRealNameInChat() {
        PlayerMock speaker = server.addPlayer("Speaker");
        PlayerMock staff = server.addPlayer("Staff");
        staff.addAttachment(plugin, PlayerDisguiseService.SEE_REAL_NAME_PERMISSION, true);
        plugin.getActiveDisguiseRegistry().assign(speaker.getUniqueId(),
                new FakeIdentity("Crimson_Wolf", "skin-value", "skin-signature"));

        AsyncChatEvent chatEvent = sendChat(speaker, "hello", bracketRenderer());

        assertFalse(staff.isOp());
        assertEquals("[Speaker] <Crimson_Wolf> hello", plainText(renderFor(chatEvent, speaker, staff)));
    }

    @Test
    void playerWithoutSeeRealPermissionDoesNotSeeRealNameInChat() {
        PlayerMock speaker = server.addPlayer("Speaker");
        PlayerMock bystander = server.addPlayer("Bystander");
        plugin.getActiveDisguiseRegistry().assign(speaker.getUniqueId(),
                new FakeIdentity("Crimson_Wolf", "skin-value", "skin-signature"));

        AsyncChatEvent chatEvent = sendChat(speaker, "hello", bracketRenderer());

        assertEquals("<Crimson_Wolf> hello", plainText(renderFor(chatEvent, speaker, bystander)));
    }

    @Test
    void operatorWithoutExplicitPermissionStillSeesRealNameInChat() {
        PlayerMock speaker = server.addPlayer("Speaker");
        PlayerMock operator = server.addPlayer("Operator");
        operator.setOp(true);
        plugin.getActiveDisguiseRegistry().assign(speaker.getUniqueId(),
                new FakeIdentity("Crimson_Wolf", "skin-value", "skin-signature"));

        AsyncChatEvent chatEvent = sendChat(speaker, "hello", bracketRenderer());

        assertEquals("[Speaker] <Crimson_Wolf> hello", plainText(renderFor(chatEvent, speaker, operator)));
    }

    @Test
    void consoleSeesRealNameInChat() {
        PlayerMock speaker = server.addPlayer("Speaker");
        plugin.getActiveDisguiseRegistry().assign(speaker.getUniqueId(),
                new FakeIdentity("Crimson_Wolf", "skin-value", "skin-signature"));

        AsyncChatEvent chatEvent = sendChat(speaker, "hello", bracketRenderer());

        assertEquals("[Speaker] <Crimson_Wolf> hello",
                plainText(renderFor(chatEvent, speaker, server.getConsoleSender())));
    }

    @Test
    void realNamePrefixIsAqua() {
        PlayerMock speaker = server.addPlayer("Speaker");
        PlayerMock operator = server.addPlayer("Operator");
        operator.setOp(true);
        plugin.getActiveDisguiseRegistry().assign(speaker.getUniqueId(),
                new FakeIdentity("Crimson_Wolf", "skin-value", "skin-signature"));

        AsyncChatEvent chatEvent = sendChat(speaker, "hello", bracketRenderer());
        Component rendered = renderFor(chatEvent, speaker, operator);

        assertNull(rendered.color());
        assertEquals(NamedTextColor.AQUA, rendered.children().get(0).color());
        assertNull(rendered.children().get(1).color());
    }

    @Test
    void chatEventOfDisguisedPlayerIsNotCancelled() {
        PlayerMock speaker = server.addPlayer("Speaker");
        plugin.getActiveDisguiseRegistry().assign(speaker.getUniqueId(),
                new FakeIdentity("Crimson_Wolf", "skin-value", "skin-signature"));

        AsyncChatEvent chatEvent = sendChat(speaker, "hello", bracketRenderer());

        assertFalse(chatEvent.isCancelled());
    }

    @Test
    void foreignRendererIsKeptAndReceivesFakeNameAsSourceDisplayName() {
        PlayerMock speaker = server.addPlayer("Speaker");
        PlayerMock bystander = server.addPlayer("Bystander");
        plugin.getActiveDisguiseRegistry().assign(speaker.getUniqueId(),
                new FakeIdentity("Crimson_Wolf", "skin-value", "skin-signature"));
        ChatRenderer foreignRenderer = ChatRenderer.viewerUnaware((source, sourceDisplayName, message) ->
                Component.text("(Rank) ").append(sourceDisplayName).append(Component.text(": ")).append(message));

        AsyncChatEvent chatEvent = sendChat(speaker, "hello", foreignRenderer);

        assertEquals("(Rank) Crimson_Wolf: hello", plainText(renderFor(chatEvent, speaker, bystander)));
    }

    @Test
    void notDisguisedPlayerChatRendererStaysUnchanged() {
        PlayerMock speaker = server.addPlayer("Speaker");
        ChatRenderer original = bracketRenderer();

        AsyncChatEvent chatEvent = sendChat(speaker, "hello", original);

        assertSame(original, chatEvent.renderer());
        assertFalse(chatEvent.isCancelled());
    }

    private ChatRenderer bracketRenderer() {
        return ChatRenderer.viewerUnaware((source, sourceDisplayName, message) ->
                Component.text("<").append(sourceDisplayName).append(Component.text("> ")).append(message));
    }

    private Component renderFor(AsyncChatEvent chatEvent, PlayerMock speaker, Audience viewer) {
        return chatEvent.renderer().render(speaker, Component.text(speaker.getName()), chatEvent.message(), viewer);
    }

    private Component vanillaNameComponent(PlayerMock player) {
        return Component.text(player.getName())
                .hoverEvent(HoverEvent.showEntity(Key.key("minecraft:player"), player.getUniqueId(),
                        Component.text(player.getName())))
                .clickEvent(ClickEvent.suggestCommand("/tell " + player.getName() + " "));
    }

    private Advancement stubAdvancement() {
        return (Advancement) Proxy.newProxyInstance(Advancement.class.getClassLoader(),
                new Class<?>[] {Advancement.class}, (proxy, method, arguments) -> null);
    }

    @Test
    void disguisedPlayerQuitMessageUsesFakeNameWithoutHoverOrClick() {
        PlayerMock target = server.addPlayer("Target");
        plugin.getActiveDisguiseRegistry().assign(target.getUniqueId(),
                new FakeIdentity("Crimson_Wolf", "skin-value", "skin-signature"));
        Component original = Component.translatable("multiplayer.player.left", NamedTextColor.YELLOW,
                vanillaNameComponent(target));
        PlayerQuitEvent quitEvent = new PlayerQuitEvent(target, original, PlayerQuitEvent.QuitReason.DISCONNECTED);

        Bukkit.getPluginManager().callEvent(quitEvent);

        assertNotNull(quitEvent.quitMessage());
        String serialized = GsonComponentSerializer.gson().serialize(quitEvent.quitMessage());
        assertFalse(serialized.contains("Target"));
        assertTrue(serialized.contains("Crimson_Wolf"));
    }

    @Test
    void notDisguisedPlayerQuitMessageStaysUnchanged() {
        PlayerMock target = server.addPlayer("Target");
        Component original = Component.translatable("multiplayer.player.left", NamedTextColor.YELLOW,
                vanillaNameComponent(target));
        PlayerQuitEvent quitEvent = new PlayerQuitEvent(target, original, PlayerQuitEvent.QuitReason.DISCONNECTED);

        Bukkit.getPluginManager().callEvent(quitEvent);

        assertEquals(original, quitEvent.quitMessage());
    }

    @Test
    void disguisedPlayerQuitMessageStaysNullWhenAlreadyNull() {
        PlayerMock target = server.addPlayer("Target");
        plugin.getActiveDisguiseRegistry().assign(target.getUniqueId(),
                new FakeIdentity("Crimson_Wolf", "skin-value", "skin-signature"));
        PlayerQuitEvent quitEvent = new PlayerQuitEvent(target, (Component) null,
                PlayerQuitEvent.QuitReason.DISCONNECTED);

        Bukkit.getPluginManager().callEvent(quitEvent);

        assertNull(quitEvent.quitMessage());
    }

    @Test
    void disguisedPlayerJoinMessageUsesFakeNameWithoutHoverOrClick() {
        PlayerMock target = server.addPlayer("Target");
        plugin.getActiveDisguiseRegistry().assign(target.getUniqueId(),
                new FakeIdentity("Crimson_Wolf", "skin-value", "skin-signature"));
        Component original = Component.translatable("multiplayer.player.joined", NamedTextColor.YELLOW,
                vanillaNameComponent(target));
        PlayerJoinEvent joinEvent = new PlayerJoinEvent(target, original);

        Bukkit.getPluginManager().callEvent(joinEvent);

        assertNotNull(joinEvent.joinMessage());
        String serialized = GsonComponentSerializer.gson().serialize(joinEvent.joinMessage());
        assertFalse(serialized.contains("Target"));
        assertTrue(serialized.contains("Crimson_Wolf"));
    }

    @Test
    void notDisguisedPlayerJoinMessageStaysUnchanged() {
        PlayerMock target = server.addPlayer("Target");
        Component original = Component.translatable("multiplayer.player.joined", NamedTextColor.YELLOW,
                vanillaNameComponent(target));
        PlayerJoinEvent joinEvent = new PlayerJoinEvent(target, original);

        Bukkit.getPluginManager().callEvent(joinEvent);

        assertEquals(original, joinEvent.joinMessage());
    }

    @Test
    void disguisedPlayerAdvancementMessageUsesFakeNameWithoutHoverOrClick() {
        PlayerMock target = server.addPlayer("Target");
        plugin.getActiveDisguiseRegistry().assign(target.getUniqueId(),
                new FakeIdentity("Crimson_Wolf", "skin-value", "skin-signature"));
        Component original = Component.translatable("chat.type.advancement.task", vanillaNameComponent(target),
                Component.text("Stone Age"));
        PlayerAdvancementDoneEvent advancementEvent =
                new PlayerAdvancementDoneEvent(target, stubAdvancement(), original);

        Bukkit.getPluginManager().callEvent(advancementEvent);

        assertNotNull(advancementEvent.message());
        String serialized = GsonComponentSerializer.gson().serialize(advancementEvent.message());
        assertFalse(serialized.contains("Target"));
        assertTrue(serialized.contains("Crimson_Wolf"));
        assertTrue(serialized.contains("Stone Age"));
    }

    @Test
    void notDisguisedPlayerAdvancementMessageStaysUnchanged() {
        PlayerMock target = server.addPlayer("Target");
        Component original = Component.translatable("chat.type.advancement.task", vanillaNameComponent(target),
                Component.text("Stone Age"));
        PlayerAdvancementDoneEvent advancementEvent =
                new PlayerAdvancementDoneEvent(target, stubAdvancement(), original);

        Bukkit.getPluginManager().callEvent(advancementEvent);

        assertEquals(original, advancementEvent.message());
    }

    @Test
    void disguisedPlayerAdvancementWithNullMessageStaysNull() {
        PlayerMock target = server.addPlayer("Target");
        plugin.getActiveDisguiseRegistry().assign(target.getUniqueId(),
                new FakeIdentity("Crimson_Wolf", "skin-value", "skin-signature"));
        PlayerAdvancementDoneEvent advancementEvent =
                new PlayerAdvancementDoneEvent(target, stubAdvancement(), null);

        assertDoesNotThrow(() -> Bukkit.getPluginManager().callEvent(advancementEvent));

        assertNull(advancementEvent.message());
    }

    private AsyncChatEvent sendChat(PlayerMock speaker, String text, ChatRenderer renderer) {
        Component message = Component.text(text);
        AsyncChatEvent chatEvent = new AsyncChatEvent(false, speaker, new HashSet<>(server.getOnlinePlayers()),
                renderer, message, message, SignedMessage.system(text, message));
        Bukkit.getPluginManager().callEvent(chatEvent);
        return chatEvent;
    }

    private String plainText(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private void drainMessages(PlayerMock player) {
        while (player.nextComponentMessage() != null) {
        }
    }

    @Test
    @Disabled("TODO: MockBukkit's PlayerSpigotMock does not implement respawn() (throws "
            + "UnsupportedOperationException), so the delayed respawn task cannot be ticked "
            + "through in a test without hitting that unimplemented method. Re-enable once "
            + "MockBukkit provides a working respawn() mock.")
    void playerEndsUpInSpectatorAtDeathLocationAfterTheRespawnTask() {
        PlayerMock player = server.addPlayer("Victim");
        player.damage(player.getHealth() + 1);

        server.getScheduler().performTicks(2L);

        assertEquals(GameMode.SPECTATOR, player.getGameMode());
    }
}
