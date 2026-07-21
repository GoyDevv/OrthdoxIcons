package com.orthodoxicons.item;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Holds the {@link NamespacedKey}s used to tag icon items and their persistent
 * data. Centralized so keys are created once and reused everywhere.
 */
public final class IconItemKeys {

    private final NamespacedKey iconId;
    private final NamespacedKey mapId;
    private final NamespacedKey marker;

    /**
     * @param plugin owning plugin for the key namespace
     */
    public IconItemKeys(Plugin plugin) {
        this.iconId = new NamespacedKey(plugin, "icon_id");
        this.mapId = new NamespacedKey(plugin, "map_id");
        this.marker = new NamespacedKey(plugin, "orthodox_icon");
    }

    /** @return key storing the icon UUID string in item/entity PDC */
    public NamespacedKey iconId() {
        return iconId;
    }

    /** @return key storing the Bukkit map id */
    public NamespacedKey mapId() {
        return mapId;
    }

    /** @return marker key (value 1) identifying OrthodoxIcons items/entities */
    public NamespacedKey marker() {
        return marker;
    }
}
