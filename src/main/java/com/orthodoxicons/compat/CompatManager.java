package com.orthodoxicons.compat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.PluginManager;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Detects optional plugins reflectively and exposes a unified
 * {@link ProtectionHook}. It never imports optional plugin classes directly at
 * runtime beyond soft checks, so a missing dependency can never cause a
 * {@code ClassNotFoundException} on the critical path.
 */
public final class CompatManager {

    private final Logger logger;
    private boolean viaVersion;
    private boolean viaBackwards;
    private boolean viaRewind;
    private ProtectionHook protection = new AllowAllHook();

    /**
     * @param logger plugin logger
     */
    public CompatManager(Logger logger) {
        this.logger = logger;
    }

    /**
     * Detects optional plugins and selects a protection hook. Safe to call on
     * enable and on reload.
     */
    public void detect() {
        PluginManager pm = Bukkit.getPluginManager();
        this.viaVersion = pm.getPlugin("ViaVersion") != null;
        this.viaBackwards = pm.getPlugin("ViaBackwards") != null;
        this.viaRewind = pm.getPlugin("ViaRewind") != null;

        if (viaVersion) {
            logger.info("ViaVersion detected - multi-version clients supported via map rendering.");
        }
        if (viaBackwards) {
            logger.info("ViaBackwards detected - older clients supported.");
        }
        if (viaRewind) {
            logger.info("ViaRewind detected - legacy clients supported.");
        }

        this.protection = selectProtection(pm);
        logger.info("Protection backend: " + protection.name());
    }

    private ProtectionHook selectProtection(PluginManager pm) {
        List<String> supported = new ArrayList<>(List.of(
                "WorldGuard", "GriefPrevention", "Lands", "Towny"));
        for (String name : supported) {
            if (pm.getPlugin(name) != null) {
                return new EventProbeHook(name);
            }
        }
        return new AllowAllHook();
    }

    /** @return whether ViaVersion is installed */
    public boolean hasViaVersion() { return viaVersion; }
    /** @return whether ViaBackwards is installed */
    public boolean hasViaBackwards() { return viaBackwards; }
    /** @return whether ViaRewind is installed */
    public boolean hasViaRewind() { return viaRewind; }

    /** @return the active protection hook */
    public ProtectionHook protection() {
        return protection;
    }

    /**
     * Default hook that allows all actions when no protection plugin is present.
     */
    private static final class AllowAllHook implements ProtectionHook {
        @Override
        public boolean canBuild(Player player, Location location) {
            return true;
        }

        @Override
        public String name() {
            return "None (all actions allowed)";
        }
    }

    /**
     * Generic protection hook that works with any plugin that cancels
     * {@link BlockBreakEvent}s for protected regions. It probes permission by
     * firing a synthetic, non-applied break event and reading its cancel state,
     * which is the most portable way to respect arbitrary protection plugins
     * without compiling against each of their APIs.
     */
    private static final class EventProbeHook implements ProtectionHook {
        private final String backendName;

        EventProbeHook(String backendName) {
            this.backendName = backendName;
        }

        @Override
        public boolean canBuild(Player player, Location location) {
            if (location.getWorld() == null) {
                return false;
            }
            BlockBreakEvent probe = new BlockBreakEvent(location.getBlock(), player);
            probe.setDropItems(false);
            try {
                Bukkit.getPluginManager().callEvent(probe);
            } catch (Throwable t) {
                // If a listener misbehaves, fall back to allowing the action.
                return true;
            }
            return !probe.isCancelled();
        }

        @Override
        public String name() {
            return backendName + " (via event probe)";
        }
    }
}
