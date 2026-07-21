package com.orthodoxicons.gui;

import com.orthodoxicons.config.ConfigManager;
import com.orthodoxicons.item.IconItemFactory;
import com.orthodoxicons.locale.LocaleManager;
import com.orthodoxicons.model.Icon;
import com.orthodoxicons.storage.FavoritesRepository;
import com.orthodoxicons.storage.IconRepository;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Handles all click interaction inside the icon browser: obtaining icons,
 * toggling favorites, pagination, sorting, category cycling, random selection,
 * search prompts and closing. Cancels every click to keep the GUI read-only.
 */
public final class GuiListener implements Listener {

    private final GuiManager gui;
    private final IconRepository icons;
    private final FavoritesRepository favorites;
    private final IconItemFactory itemFactory;
    private final LocaleManager locale;
    private final ConfigManager config;
    private final SearchPrompt searchPrompt;
    private final Random random = new Random();

    /**
     * @param gui          gui manager
     * @param icons        icon repository
     * @param favorites    favorites repository
     * @param itemFactory  item factory
     * @param locale       locale manager
     * @param config       configuration manager
     * @param searchPrompt chat/anvil search prompt helper
     */
    public GuiListener(GuiManager gui, IconRepository icons, FavoritesRepository favorites,
                       IconItemFactory itemFactory, LocaleManager locale, ConfigManager config,
                       SearchPrompt searchPrompt) {
        this.gui = gui;
        this.icons = icons;
        this.favorites = favorites;
        this.itemFactory = itemFactory;
        this.locale = locale;
        this.config = config;
        this.searchPrompt = searchPrompt;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof BrowserHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holderRaw = event.getInventory().getHolder();
        if (!(holderRaw instanceof BrowserHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owns(player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        int size = top.getSize();
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= size) {
            return;
        }
        int navBase = size - 9;
        if (slot >= navBase) {
            handleNav(player, holder, slot - navBase);
            return;
        }
        handleContentClick(player, holder, slot, event.getClick());
    }

    private void handleContentClick(Player player, BrowserHolder holder, int slot, ClickType click) {
        int contentSlots = holder.getInventory().getSize() - 9;
        int index = holder.page() * contentSlots + slot;
        List<Icon> snapshot = holder.snapshot();
        if (index < 0 || index >= snapshot.size()) {
            return;
        }
        Icon icon = snapshot.get(index);
        if (click.isShiftClick()) {
            favorites.toggle(player.getUniqueId(), icon.id());
            gui.render(player, holder);
            return;
        }
        giveIcon(player, icon);
    }

    private void handleNav(Player player, BrowserHolder holder, int navSlot) {
        switch (navSlot) {
            case GuiManager.NAV_PREV -> {
                holder.setPage(holder.page() - 1);
                gui.render(player, holder);
            }
            case GuiManager.NAV_NEXT -> {
                holder.setPage(holder.page() + 1);
                gui.render(player, holder);
            }
            case GuiManager.NAV_SEARCH -> searchPrompt.begin(player, holder);
            case GuiManager.NAV_CATEGORIES -> cycleCategory(player, holder);
            case GuiManager.NAV_FAVORITES -> {
                holder.setFavoritesOnly(!holder.favoritesOnly());
                holder.setPage(0);
                gui.render(player, holder);
            }
            case GuiManager.NAV_RANDOM -> randomIcon(player);
            case GuiManager.NAV_SORT -> {
                holder.setSort(holder.sort().next());
                gui.render(player, holder);
            }
            case GuiManager.NAV_CLOSE -> player.closeInventory();
            case GuiManager.NAV_PAGE -> { /* page indicator, no action */ }
            default -> { /* ignore */ }
        }
    }

    private void cycleCategory(Player player, BrowserHolder holder) {
        List<String> categories = icons.categories();
        if (categories.isEmpty()) {
            return;
        }
        String current = holder.category();
        int next;
        if (current.isBlank()) {
            next = 0;
        } else {
            int idx = categories.indexOf(current);
            next = idx + 1;
        }
        if (next >= categories.size()) {
            holder.setCategory(""); // wrap back to "All"
        } else {
            holder.setCategory(categories.get(next));
        }
        holder.setPage(0);
        gui.render(player, holder);
    }

    private void randomIcon(Player player) {
        List<Icon> all = icons.all();
        if (all.isEmpty()) {
            locale.send(player, "gui.empty");
            return;
        }
        Icon icon = all.get(random.nextInt(all.size()));
        player.closeInventory();
        giveIcon(player, icon);
        locale.send(player, "command.random.given", "icon", icon.name());
    }

    private void giveIcon(Player player, Icon icon) {
        ItemStack item = itemFactory.create(icon, 1);
        var leftover = player.getInventory().addItem(item);
        leftover.values().forEach(rest ->
                player.getWorld().dropItemNaturally(player.getLocation(), rest));
    }

    /**
     * Resolves the icon id backing a preview item, if any.
     *
     * @param item the clicked item
     * @return the icon id
     */
    public Optional<UUID> resolveIcon(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return Optional.empty();
        }
        return itemFactory.readIconId(item);
    }

    /**
     * Re-renders all currently-open browsers (used after a background update so
     * new icons appear without re-opening). Must run on the main thread.
     */
    public void refreshOpenBrowsers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof BrowserHolder holder
                    && holder.owns(player)) {
                gui.render(player, holder);
            }
        }
    }
}
