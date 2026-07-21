package com.orthodoxicons.compat;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Abstraction over land-protection plugins. The default implementation permits
 * everything; concrete hooks are only wired up when the corresponding plugin is
 * present, so the plugin never hard-depends on any protection API and never
 * crashes when they are absent.
 */
public interface ProtectionHook {

    /**
     * @param player   the acting player
     * @param location the target location
     * @return {@code true} if the player may build/interact at the location
     */
    boolean canBuild(Player player, Location location);

    /**
     * @return a human-readable name of the backing protection provider
     */
    String name();
}
