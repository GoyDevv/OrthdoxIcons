package com.orthodoxicons.gui;

import com.orthodoxicons.config.ConfigManager;
import com.orthodoxicons.locale.LocaleManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements keyboard search for the browser. Because a portable, version-
 * agnostic anvil text input is not guaranteed across the full 1.20.1-26.2 range
 * (and to avoid version-specific packets that could break with Via*), this uses
 * a chat capture prompt: the player types their query, which is captured, the
 * event cancelled, and the browser re-opened filtered. This works identically
 * on every client version routed through the Via* ecosystem.
 */
public final class SearchPrompt implements Listener {

    private final Plugin plugin;
    private final ConfigManager config;
    private final LocaleManager locale;
    private final Map<UUID, BrowserHolder> pending = new ConcurrentHashMap<>();
    private GuiManager gui;

    /**
     * @param plugin owning plugin
     * @param config configuration manager
     * @param locale locale manager
     */
    public SearchPrompt(Plugin plugin, ConfigManager config, LocaleManager locale) {
        this.plugin = plugin;
        this.config = config;
        this.locale = locale;
    }

    /**
     * Wires the GUI manager (set after construction to avoid a cycle).
     *
     * @param gui gui manager
     */
    public void setGui(GuiManager gui) {
        this.gui = gui;
    }

    /**
     * Begins a search prompt for a player.
     *
     * @param player the searching player
     * @param holder the active browser holder
     */
    public void begin(Player player, BrowserHolder holder) {
        pending.put(player.getUniqueId(), holder);
        player.closeInventory();
        locale.send(player, "gui.search-prompt");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        BrowserHolder holder = pending.remove(player.getUniqueId());
        if (holder == null) {
            return;
        }
        event.setCancelled(true);
        String query = event.getMessage().trim();
        // Re-open the browser on the main thread with the new filter applied.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (gui == null) {
                return;
            }
            holder.setFavoritesOnly(false);
            holder.setCategory("");
            holder.setSearch(query.equalsIgnoreCase("cancel") ? "" : query);
            holder.setPage(0);
            gui.render(player, holder);
        });
    }

    /**
     * Clears a pending prompt (e.g. on quit).
     *
     * @param player the player
     */
    public void clear(Player player) {
        pending.remove(player.getUniqueId());
    }

    /** @return whether anvil-style search is preferred (config-driven). */
    public boolean prefersAnvil() {
        return config.get().anvilSearch();
    }
}
