package com.orthodoxicons.locale;

import com.orthodoxicons.config.ConfigManager;
import com.orthodoxicons.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads localized message strings from {@code lang/<locale>.yml} with overrides
 * from {@code messages.yml}, and formats them with placeholder substitution.
 * New languages are added simply by dropping a new {@code lang/<code>.yml} file
 * and pointing {@code locale} at it, so the architecture supports translation
 * without code changes.
 */
public final class LocaleManager {

    private final Plugin plugin;
    private final ConfigManager config;
    private final Map<String, String> messages = new ConcurrentHashMap<>();
    private volatile String prefix = "";

    /**
     * @param plugin owning plugin
     * @param config configuration manager
     */
    public LocaleManager(Plugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * (Re)loads the active language file and message overrides.
     */
    public void load() {
        messages.clear();
        String locale = config.get().locale();
        String path = "lang/" + locale + ".yml";

        // Ensure the requested language file exists on disk; fall back to en.
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) {
            if (plugin.getResource(path) != null) {
                plugin.saveResource(path, false);
            } else {
                path = "lang/en.yml";
            }
        }

        FileConfiguration lang = config.loadYaml(path);
        flatten("", lang.getValues(true));

        // Apply overrides from messages.yml.
        FileConfiguration overrides = config.loadYaml("messages.yml");
        if (overrides.isConfigurationSection("overrides")) {
            for (Map.Entry<String, Object> entry :
                    overrides.getConfigurationSection("overrides").getValues(true).entrySet()) {
                if (entry.getValue() instanceof String s) {
                    messages.put(entry.getKey(), s);
                }
            }
        }
        this.prefix = messages.getOrDefault("prefix", "");
    }

    private void flatten(String parent, Map<String, Object> values) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = parent.isEmpty() ? entry.getKey() : parent + "." + entry.getKey();
            if (entry.getValue() instanceof String s) {
                messages.put(key, s);
            }
        }
    }

    /**
     * Returns a raw, colorized message with placeholder substitution but no
     * prefix.
     *
     * @param key          message key
     * @param replacements alternating placeholder/value pairs
     * @return the formatted string ({@code key} itself if missing)
     */
    public String raw(String key, String... replacements) {
        String template = messages.getOrDefault(key, key);
        template = applyPlaceholders(template, replacements);
        return Text.color(template);
    }

    /**
     * Sends a prefixed, colorized message to a recipient.
     *
     * @param sender       recipient
     * @param key          message key
     * @param replacements alternating placeholder/value pairs
     */
    public void send(CommandSender sender, String key, String... replacements) {
        sender.sendMessage(Text.color(prefix) + raw(key, replacements));
    }

    private static String applyPlaceholders(String template, String... replacements) {
        String result = template;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            result = result.replace("%" + replacements[i] + "%", replacements[i + 1]);
        }
        return result;
    }
}
