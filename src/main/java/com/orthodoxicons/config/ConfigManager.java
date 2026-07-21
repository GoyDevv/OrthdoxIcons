package com.orthodoxicons.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Loads, saves defaults for, and hot-reloads {@code config.yml}. Exposes the
 * current {@link PluginConfig} snapshot via an atomic reference so it can be
 * read safely from asynchronous tasks.
 */
public final class ConfigManager {

    private final Plugin plugin;
    private final AtomicReference<PluginConfig> current = new AtomicReference<>();
    private volatile boolean debugOverride;
    private volatile boolean debugOverridden;

    /**
     * @param plugin owning plugin used for data folder + default resources
     */
    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Ensures the bundled defaults exist on disk and loads the configuration.
     */
    public void load() {
        plugin.saveDefaultConfig();
        // Ensure auxiliary resources exist on first run.
        saveResourceIfMissing("messages.yml");
        saveResourceIfMissing("lang/en.yml");
        saveResourceIfMissing("curated-icons.json");
        reload();
    }

    /**
     * Reloads the configuration from disk, replacing the current snapshot.
     */
    public void reload() {
        plugin.reloadConfig();
        FileConfiguration fc = plugin.getConfig();
        current.set(new PluginConfig(fc));
        debugOverridden = false;
    }

    /**
     * @return the current immutable configuration snapshot
     */
    public PluginConfig get() {
        PluginConfig cfg = current.get();
        if (cfg == null) {
            reload();
            cfg = current.get();
        }
        return cfg;
    }

    /**
     * @return the effective debug flag, honoring a runtime override toggle
     */
    public boolean debug() {
        return debugOverridden ? debugOverride : get().debug();
    }

    /**
     * Toggles debug mode at runtime without editing the file.
     *
     * @return the new debug state
     */
    public boolean toggleDebug() {
        boolean next = !debug();
        debugOverride = next;
        debugOverridden = true;
        return next;
    }

    private void saveResourceIfMissing(String path) {
        File target = new File(plugin.getDataFolder(), path);
        if (!target.exists()) {
            plugin.saveResource(path, false);
        }
    }

    /**
     * Loads an arbitrary YAML resource from the data folder, falling back to the
     * bundled copy inside the jar when the on-disk file is missing.
     *
     * @param path relative resource path
     * @return a loaded {@link YamlConfiguration} (possibly empty)
     */
    public FileConfiguration loadYaml(String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (file.exists()) {
            return YamlConfiguration.loadConfiguration(file);
        }
        return new YamlConfiguration();
    }
}
