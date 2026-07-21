package com.orthodoxicons.gui;

import com.orthodoxicons.model.Icon;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@link InventoryHolder} carrying the browser's per-view state (current page,
 * filter, sort, search text, favorites mode). Using a holder lets the click
 * listener recover the exact state of the inventory that was clicked, which is
 * the robust, race-free way to build stateful Bukkit GUIs.
 */
public final class BrowserHolder implements InventoryHolder {

    private final UUID viewer;
    private Inventory inventory;
    private int page;
    private String search = "";
    private String category = "";
    private boolean favoritesOnly;
    private GuiSort sort;
    private List<Icon> snapshot = new ArrayList<>();

    /**
     * @param viewer the viewing player's uuid
     * @param sort   initial sort mode
     */
    public BrowserHolder(UUID viewer, GuiSort sort) {
        this.viewer = viewer;
        this.sort = sort;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Binds the backing inventory (set once after creation).
     *
     * @param inventory the inventory
     */
    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public UUID viewer() { return viewer; }
    public int page() { return page; }
    public void setPage(int page) { this.page = Math.max(0, page); }
    public String search() { return search; }
    public void setSearch(String search) { this.search = search == null ? "" : search; }
    public String category() { return category; }
    public void setCategory(String category) { this.category = category == null ? "" : category; }
    public boolean favoritesOnly() { return favoritesOnly; }
    public void setFavoritesOnly(boolean favoritesOnly) { this.favoritesOnly = favoritesOnly; }
    public GuiSort sort() { return sort; }
    public void setSort(GuiSort sort) { this.sort = sort; }
    public List<Icon> snapshot() { return snapshot; }
    public void setSnapshot(List<Icon> snapshot) { this.snapshot = snapshot; }

    /**
     * @param player the player to check
     * @return whether this holder belongs to the given player
     */
    public boolean owns(Player player) {
        return player.getUniqueId().equals(viewer);
    }
}
