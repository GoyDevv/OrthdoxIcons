package com.orthodoxicons.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Cleans up transient per-player session state (pending search prompts) when a
 * player disconnects, preventing stale captures from firing later.
 */
public final class PlayerSessionListener implements Listener {

    private final SearchPrompt searchPrompt;

    /**
     * @param searchPrompt the search prompt helper
     */
    public PlayerSessionListener(SearchPrompt searchPrompt) {
        this.searchPrompt = searchPrompt;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        searchPrompt.clear(event.getPlayer());
    }
}
