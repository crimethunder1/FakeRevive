package com.deathrevive.plugin.message;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Loads localised message strings from a YAML resource bundled in the plugin jar
 * ({@code messages_<language>.yml}) and provides simple placeholder substitution.
 * Falls back to English if the requested language file does not exist.
 */
public class MessageService {

    private final YamlConfiguration messages;

    public MessageService(JavaPlugin plugin, String language) {
        InputStream stream = plugin.getResource("messages_" + language + ".yml");
        if (stream == null) {
            stream = plugin.getResource("messages_en.yml");
        }

        this.messages = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    /** @return the message for the given dot-separated key, or the key itself if not found. */
    public String get(String key) {
        return messages.getString(key, key);
    }

    /**
     * Returns the message for the given key with placeholder values substituted.
     * Placeholders are supplied as consecutive name-value pairs:
     * {@code get("key", "player", "Steve", "count", "3")} replaces {@code {player}} and
     * {@code {count}} in the message string.
     * @return the formatted message, or the key itself if the key is not found.
     */
    public String get(String key, String... placeholders) {
        String message = get(key);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            message = message.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        return message;
    }
}
