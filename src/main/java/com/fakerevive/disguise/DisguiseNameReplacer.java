package com.fakerevive.disguise;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.KeybindComponent;
import net.kyori.adventure.text.ScoreComponent;
import net.kyori.adventure.text.SelectorComponent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

public final class DisguiseNameReplacer {
    private DisguiseNameReplacer() {
    }

    public static Component replace(Component component, String realName, String fakeName) {
        if (component == null || realName == null || realName.isEmpty() || fakeName == null) {
            return component;
        }
        return replaceComponent(component, realName, fakeName);
    }

    private static Component replaceComponent(Component component, String realName, String fakeName) {
        boolean contentContainsRealName = contentContainsRealName(component, realName);
        Component replaced = replaceContent(component, realName, fakeName);
        replaced = replaced.style(sanitizeStyle(component.style(), contentContainsRealName, realName));

        List<Component> replacedChildren = new ArrayList<>();
        for (Component child : component.children()) {
            replacedChildren.add(replaceComponent(child, realName, fakeName));
        }
        return replaced.children(replacedChildren);
    }

    private static boolean contentContainsRealName(Component component, String realName) {
        if (component instanceof TextComponent textComponent) {
            return textComponent.content().contains(realName);
        }
        if (component instanceof TranslatableComponent translatableComponent) {
            String fallback = translatableComponent.fallback();
            return fallback != null && fallback.contains(realName);
        }
        if (component instanceof ScoreComponent scoreComponent) {
            return scoreComponent.name().contains(realName);
        }
        if (component instanceof SelectorComponent selectorComponent) {
            return selectorComponent.pattern().contains(realName);
        }
        if (component instanceof KeybindComponent keybindComponent) {
            return keybindComponent.keybind().contains(realName);
        }
        return false;
    }

    private static Component replaceContent(Component component, String realName, String fakeName) {
        if (component instanceof TextComponent textComponent) {
            return textComponent.content(textComponent.content().replace(realName, fakeName));
        }
        if (component instanceof TranslatableComponent translatableComponent) {
            return replaceTranslatable(translatableComponent, realName, fakeName);
        }
        if (component instanceof ScoreComponent scoreComponent) {
            return scoreComponent.name(scoreComponent.name().replace(realName, fakeName));
        }
        if (component instanceof SelectorComponent selectorComponent) {
            return selectorComponent.pattern(selectorComponent.pattern().replace(realName, fakeName));
        }
        return component;
    }

    private static Component replaceTranslatable(TranslatableComponent translatableComponent, String realName,
                                                 String fakeName) {
        List<TranslationArgument> replacedArguments = new ArrayList<>();
        for (TranslationArgument argument : translatableComponent.arguments()) {
            if (argument.value() instanceof Component argumentComponent) {
                replacedArguments.add(TranslationArgument.component(
                        replaceComponent(argumentComponent, realName, fakeName)));
            } else {
                replacedArguments.add(argument);
            }
        }

        TranslatableComponent replaced = translatableComponent.arguments(replacedArguments);
        String fallback = translatableComponent.fallback();
        if (fallback != null) {
            replaced = replaced.fallback(fallback.replace(realName, fakeName));
        }
        return replaced;
    }

    private static Style sanitizeStyle(Style style, boolean contentContainsRealName, String realName) {
        Style sanitized = style;

        HoverEvent<?> hoverEvent = style.hoverEvent();
        if (hoverEvent != null && (contentContainsRealName || hoverContainsRealName(hoverEvent, realName))) {
            sanitized = sanitized.hoverEvent(null);
        }

        ClickEvent clickEvent = style.clickEvent();
        if (clickEvent != null && (contentContainsRealName || clickEventContainsRealName(clickEvent, realName))) {
            sanitized = sanitized.clickEvent(null);
        }

        String insertion = style.insertion();
        if (insertion != null && (contentContainsRealName || insertion.contains(realName))) {
            sanitized = sanitized.insertion(null);
        }

        return sanitized;
    }

    private static boolean clickEventContainsRealName(ClickEvent<?> clickEvent, String realName) {
        return clickEvent.payload() instanceof ClickEvent.Payload.Text text && text.value().contains(realName);
    }

    private static boolean hoverContainsRealName(HoverEvent<?> hoverEvent, String realName) {
        String serialized = GsonComponentSerializer.gson().serialize(Component.empty().hoverEvent(hoverEvent));
        return serialized.contains(realName);
    }
}
