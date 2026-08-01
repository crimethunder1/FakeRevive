package com.deathrevive.plugin.message;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MessageService {

    private final YamlConfiguration messages;

    public MessageService(JavaPlugin plugin, String language) {
        InputStream stream = plugin.getResource("messages_" + language + ".yml");
        if (stream == null) {
            stream = plugin.getResource("messages_en.yml");
        }

        this.messages = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    public String get(String key) {
        return messages.getString(key, key);
    }

    public String get(String key, String... placeholders) {
        String message = get(key);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            message = message.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        return message;
    }
}
