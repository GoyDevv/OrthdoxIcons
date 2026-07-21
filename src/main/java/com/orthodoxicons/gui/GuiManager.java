package com.orthodoxicons.gui;

import com.orthodoxicons.config.ConfigManager;
import com.orthodoxicons.config.PluginConfig;
import com.orthodoxicons.item.IconItemFactory;
import com.orthodoxicons.locale.LocaleManager;
import com.orthodoxicons.model.Icon;
import com.orthodoxicons.storage.FavoritesRepository;
import com.orthodoxicons.storage.IconRepository;
import com.orthodoxicons.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds and renders the paginated, filterable icon browser inventory. The last
 * row is a navigation bar (previous/next, page indicator, search, categories,
 * favorites, random, sort, close). Content slots hold live icon items.
 */
public final class GuiManager {

    private static final int NAV_ROW_SLOTS = 9;

    private final ConfigManager config;
    private final LocaleManager locale;
    private final IconRepository icons;
    private final FavoritesRepository favorites;
    private final IconItemFactory itemFactory;

    /** Navigation slot indices, resolved relative to inventory size. */
    public static final int NAV_PREV = 0;
    public static final int NAV_SEARCH = 1;
    public static final int NAV_CATEGORIES = 2;
    public static final int NAV_FAVORITES = 3;
    public static final int NAV_PAGE = 4;
    public static final int NAV_RANDOM = 5;
    public static final int NAV_SORT = 6;
    public static final int NAV_CLOSE = 7;
    public static final int NAV_NEXT = 8;

    /**
     * @param config      configuration manager
     * @param locale      locale manager
     * @param icons       icon repository
     * @param favorites   favorites repository
     * @param itemFactory item factory
     */
    public GuiManager(ConfigManager config, LocaleManager locale, IconRepository icons,
                      FavoritesRepository favorites, IconItemFactory itemFactory) {
        this.config = config;
        this.locale = locale;
        this.icons = icons;
        this.favorites = favorites;
        this.itemFactory = itemFactory;
    }

    /**
     * Opens a fresh browser for a player.
     *
     * @param player the viewer
     */
    public void open(Player player) {
        BrowserHolder holder = new BrowserHolder(player.getUniqueId(),
                GuiSort.parse(config.get().defaultSort()));
        render(player, holder);
    }

    /**
     * Renders (or re-renders) the browser for a holder's current state.
     *
     * @param player the viewer
     * @param holder the view state
     */
    public void render(Player player, BrowserHolder holder) {
        PluginConfig cfg = config.get();
        int rows = cfg.guiRows();
        int size = rows * 9;
        int contentSlots = size - NAV_ROW_SLOTS;

        List<Icon> filtered = filter(holder, player.getUniqueId());
        filtered.sort(holder.sort().comparator());
        holder.setSnapshot(filtered);

        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) contentSlots));
        if (holder.page() >= totalPages) {
            holder.setPage(totalPages - 1);
        }

        Inventory inv = holder.getInventory();
        if (inv == null || inv.getSize() != size) {
            inv = Bukkit.createInventory(holder, size, Text.color(cfg.guiTitle()));
            holder.setInventory(inv);
        } else {
            inv.clear();
        }

        int start = holder.page() * contentSlots;
        for (int i = 0; i < contentSlots && start + i < filtered.size(); i++) {
            Icon icon = filtered.get(start + i);
            inv.setItem(i, decorateForGui(icon, player.getUniqueId()));
        }

        renderNav(inv, holder, size, totalPages);

        if (!player.getOpenInventory().getTopInventory().equals(inv)) {
            player.openInventory(inv);
        }
    }

    private List<Icon> filter(BrowserHolder holder, UUID viewer) {
        List<Icon> base;
        if (holder.favoritesOnly()) {
            Set<UUID> favs = favorites.favorites(viewer);
            base = favs.stream()
                    .map(icons::find)
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .collect(Collectors.toList());
        } else if (!holder.search().isBlank()) {
            base = icons.search(holder.search());
        } else {
            base = icons.all();
        }
        if (!holder.category().isBlank()) {
            base = base.stream()
                    .filter(i -> i.category().equalsIgnoreCase(holder.category()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>(base);
    }

    private ItemStack decorateForGui(Icon icon, UUID viewer) {
        // Preview uses the actual rendered map item so players see the artwork.
        ItemStack item = itemFactory.create(icon, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(locale.raw("gui.nav.obtain"));
            lore.add(favorites.isFavorite(viewer, icon.id())
                    ? locale.raw("gui.nav.favorite-remove")
                    : locale.raw("gui.nav.favorite-add"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void renderNav(Inventory inv, BrowserHolder holder, int size, int totalPages) {
        int base = size - NAV_ROW_SLOTS;
        inv.setItem(base + NAV_PREV, navItem(Material.ARROW, locale.raw("gui.nav.previous")));
        inv.setItem(base + NAV_SEARCH, navItem(Material.OAK_SIGN, locale.raw("gui.nav.search")));
        inv.setItem(base + NAV_CATEGORIES, navItem(Material.BOOKSHELF, locale.raw("gui.nav.categories")));
        inv.setItem(base + NAV_FAVORITES, navItem(
                holder.favoritesOnly() ? Material.NETHER_STAR : Material.FIREWORK_STAR,
                locale.raw("gui.nav.favorites")));
        inv.setItem(base + NAV_PAGE, navItem(Material.PAPER,
                locale.raw("gui.nav.page-info", "page", String.valueOf(holder.page() + 1),
                        "pages", String.valueOf(totalPages))));
        inv.setItem(base + NAV_RANDOM, navItem(Material.ENDER_PEARL, locale.raw("gui.nav.random")));
        inv.setItem(base + NAV_SORT, navItem(Material.COMPARATOR,
                locale.raw("gui.nav.sort", "sort", holder.sort().name())));
        inv.setItem(base + NAV_CLOSE, navItem(Material.BARRIER, locale.raw("gui.nav.close")));
        inv.setItem(base + NAV_NEXT, navItem(Material.ARROW, locale.raw("gui.nav.next")));
    }

    private ItemStack navItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }
}
