package com.fakerevive.message;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MessageService {
    public static final Component PREFIX = Component.text("[FakeRevive] ", NamedTextColor.AQUA);

    private YamlConfiguration messages;

    public MessageService(JavaPlugin plugin, String language) {
        loadLanguage(plugin, language);
    }

    public void reload(JavaPlugin plugin) {
        loadLanguage(plugin, plugin.getConfig().getString("language", "en"));
    }

    public String get(String key) {
        return messages.getString(key, key);
    }

    public String get(String key, String... placeholders) {
        String message = get(key);
        for (int index = 0; index + 1 < placeholders.length; index += 2) {
            message = message.replace("{" + placeholders[index] + "}", placeholders[index + 1]);
        }
        return message;
    }

    public Component prefixed(String key, NamedTextColor color, String... placeholders) {
        return PREFIX.append(unprefixed(key, color, placeholders));
    }

    public Component unprefixed(String key, NamedTextColor color, String... placeholders) {
        return Component.text(get(key, placeholders), color);
    }

    public void send(Audience audience, String key, NamedTextColor color, String... placeholders) {
        audience.sendMessage(prefixed(key, color, placeholders));
    }

    public void sendWithoutPrefix(Audience audience, String key, NamedTextColor color, String... placeholders) {
        audience.sendMessage(unprefixed(key, color, placeholders));
    }

    private void loadLanguage(JavaPlugin plugin, String language) {
        InputStream stream = plugin.getResource("messages_" + language + ".yml");
        if (stream == null) {
            stream = plugin.getResource("messages_en.yml");
        }

        this.messages = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}
