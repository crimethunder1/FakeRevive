package com.fakerevive.disguise;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisguiseNameReplacerTest {
    private static final String REAL_NAME = "RealPlayer";
    private static final String FAKE_NAME = "Crimson_Wolf";

    private Component vanillaNameComponent() {
        return Component.text(REAL_NAME)
                .hoverEvent(HoverEvent.showEntity(Key.key("minecraft:player"), UUID.randomUUID(),
                        Component.text(REAL_NAME)))
                .clickEvent(ClickEvent.suggestCommand("/tell " + REAL_NAME + " "))
                .insertion(REAL_NAME);
    }

    private String plainText(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private String json(Component component) {
        return GsonComponentSerializer.gson().serialize(component);
    }

    @Test
    void replacesRealNameInPlainText() {
        Component message = Component.text(REAL_NAME + " left the game", NamedTextColor.YELLOW);

        Component result = DisguiseNameReplacer.replace(message, REAL_NAME, FAKE_NAME);

        assertEquals(FAKE_NAME + " left the game", plainText(result));
        assertEquals(NamedTextColor.YELLOW, result.color());
    }

    @Test
    void replacesRealNameInChildComponents() {
        Component message = Component.text("Hello ").append(Component.text(REAL_NAME));

        Component result = DisguiseNameReplacer.replace(message, REAL_NAME, FAKE_NAME);

        assertEquals("Hello " + FAKE_NAME, plainText(result));
    }

    @Test
    void replacesRealNameInTranslatableArguments() {
        Component message = Component.translatable("death.attack.generic", vanillaNameComponent());

        Component result = DisguiseNameReplacer.replace(message, REAL_NAME, FAKE_NAME);

        TranslatableComponent translatable = (TranslatableComponent) result;
        assertEquals("death.attack.generic", translatable.key());
        assertEquals(1, translatable.arguments().size());
        assertEquals(FAKE_NAME, plainText((Component) translatable.arguments().get(0).value()));
        assertFalse(json(result).contains(REAL_NAME));
    }

    @Test
    void replacesRealNameInNestedTranslatableArguments() {
        Component inner = Component.translatable("chat.type.advancement.task", vanillaNameComponent());
        Component message = Component.translatable("outer.key", inner);

        Component result = DisguiseNameReplacer.replace(message, REAL_NAME, FAKE_NAME);

        assertFalse(json(result).contains(REAL_NAME));
        assertTrue(json(result).contains(FAKE_NAME));
    }

    @Test
    void removesHoverClickAndInsertionFromComponentContainingRealName() {
        Component result = DisguiseNameReplacer.replace(vanillaNameComponent(), REAL_NAME, FAKE_NAME);

        assertEquals(FAKE_NAME, plainText(result));
        assertNull(result.hoverEvent());
        assertNull(result.clickEvent());
        assertNull(result.insertion());
        assertFalse(json(result).contains(REAL_NAME));
    }

    @Test
    void removesHoverEventWhoseContentContainsRealNameEvenWhenTextDoesNot() {
        Component message = Component.text("hover me")
                .hoverEvent(HoverEvent.showText(Component.text("Name: " + REAL_NAME)));

        Component result = DisguiseNameReplacer.replace(message, REAL_NAME, FAKE_NAME);

        assertNull(result.hoverEvent());
        assertFalse(json(result).contains(REAL_NAME));
    }

    @Test
    void removesClickEventWhoseValueContainsRealNameEvenWhenTextDoesNot() {
        Component message = Component.text("click me")
                .clickEvent(ClickEvent.runCommand("/msg " + REAL_NAME + " hi"));

        Component result = DisguiseNameReplacer.replace(message, REAL_NAME, FAKE_NAME);

        assertNull(result.clickEvent());
        assertFalse(json(result).contains(REAL_NAME));
    }

    @Test
    void removesInsertionContainingRealNameEvenWhenTextDoesNot() {
        Component message = Component.text("shift click me").insertion(REAL_NAME);

        Component result = DisguiseNameReplacer.replace(message, REAL_NAME, FAKE_NAME);

        assertNull(result.insertion());
    }

    @Test
    void keepsHoverClickAndInsertionOfUnrelatedComponents() {
        Component message = Component.text("unrelated")
                .hoverEvent(HoverEvent.showText(Component.text("tooltip")))
                .clickEvent(ClickEvent.openUrl("https://example.com"))
                .insertion("other");

        Component result = DisguiseNameReplacer.replace(message, REAL_NAME, FAKE_NAME);

        assertNotNull(result.hoverEvent());
        assertNotNull(result.clickEvent());
        assertEquals("other", result.insertion());
        assertEquals(message, result);
    }

    @Test
    void leavesMessageWithoutRealNameUnchanged() {
        Component message = Component.translatable("death.attack.generic", Component.text("SomeoneElse"));

        Component result = DisguiseNameReplacer.replace(message, REAL_NAME, FAKE_NAME);

        assertEquals(message, result);
    }

    @Test
    void returnsNullForNullMessage() {
        assertNull(DisguiseNameReplacer.replace(null, REAL_NAME, FAKE_NAME));
    }

    @Test
    void returnsSameInstanceForEmptyRealName() {
        Component message = Component.text("message");

        assertSame(message, DisguiseNameReplacer.replace(message, "", FAKE_NAME));
    }
}
