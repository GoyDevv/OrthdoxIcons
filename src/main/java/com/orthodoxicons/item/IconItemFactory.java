package com.orthodoxicons.item;

import com.orthodoxicons.cache.CacheManager;
import com.orthodoxicons.config.ConfigManager;
import com.orthodoxicons.config.PluginConfig;
import com.orthodoxicons.image.ImageProcessor;
import com.orthodoxicons.locale.LocaleManager;
import com.orthodoxicons.model.Icon;
import com.orthodoxicons.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory (Factory Pattern) that turns an {@link Icon} domain object into a
 * fully-decorated, PDC-tagged {@link ItemStack}. Each icon maps to a persistent
 * Bukkit {@link MapView} whose renderer draws the cached texture; the view is
 * created lazily and memoized per icon so map ids stay stable across the run.
 */
public final class IconItemFactory {

    private final ConfigManager config;
    private final LocaleManager locale;
    private final CacheManager cache;
    private final ImageProcessor images;
    private final IconItemKeys keys;
    private final ConcurrentHashMap<UUID, Integer> mapIdByIcon = new ConcurrentHashMap<>();

    /**
     * @param config configuration manager
     * @param locale locale manager for lore labels
     * @param cache  cache manager
     * @param images image processor
     * @param keys   namespaced keys
     */
    public IconItemFactory(ConfigManager config, LocaleManager locale, CacheManager cache,
                           ImageProcessor images, IconItemKeys keys) {
        this.config = config;
        this.locale = locale;
        this.cache = cache;
        this.images = images;
        this.keys = keys;
    }

    /**
     * Creates a display item stack for an icon.
     *
     * @param icon   the icon
     * @param amount stack size
     * @return the decorated item (never {@code null})
     */
    public ItemStack create(Icon icon, int amount) {
        PluginConfig cfg = config.get();
        Material material = resolveMaterial(cfg);
        ItemStack item = new ItemStack(material, Math.max(1, amount));

        if (material == Material.FILLED_MAP) {
            applyMap(item, icon);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&d" + icon.name()));
            meta.setLore(buildLore(icon, cfg));
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(keys.iconId(), PersistentDataType.STRING, icon.id().toString());
            pdc.set(keys.marker(), PersistentDataType.INTEGER, 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Resolves or creates the persistent map view for an icon and binds a
     * fresh {@link IconMapRenderer}. Must run on the main thread.
     *
     * @param icon the icon
     * @return the map view id
     */
    public int mapIdFor(Icon icon) {
        Integer cached = mapIdByIcon.get(icon.id());
        if (cached != null) {
            MapView existing = Bukkit.getMap(cached);
            if (existing != null) {
                return cached;
            }
        }
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (world == null) {
            return -1;
        }
        MapView view = Bukkit.createMap(world);
        view.setScale(MapView.Scale.CLOSEST);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        for (MapRenderer renderer : new ArrayList<>(view.getRenderers())) {
            view.removeRenderer(renderer);
        }
        view.addRenderer(new IconMapRenderer(icon.id(), cache, images));
        mapIdByIcon.put(icon.id(), view.getId());
        return view.getId();
    }

    /**
     * Reads the icon UUID stored in an item's PDC.
     *
     * @param item candidate item
     * @return the icon id if the item is an OrthodoxIcons item
     */
    public Optional<UUID> readIconId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String raw = pdc.get(keys.iconId(), PersistentDataType.STRING);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * @param item candidate
     * @return whether the item is an OrthodoxIcons icon item
     */
    public boolean isIconItem(ItemStack item) {
        return readIconId(item).isPresent();
    }

    private void applyMap(ItemStack item, Icon icon) {
        int mapId = mapIdFor(icon);
        if (mapId < 0) {
            return;
        }
        MapMeta mapMeta = (MapMeta) item.getItemMeta();
        if (mapMeta == null) {
            return;
        }
        MapView view = Bukkit.getMap(mapId);
        if (view != null) {
            mapMeta.setMapView(view);
        }
        mapMeta.getPersistentDataContainer()
                .set(keys.mapId(), PersistentDataType.INTEGER, mapId);
        item.setItemMeta(mapMeta);
    }

    private List<String> buildLore(Icon icon, PluginConfig cfg) {
        List<String> lore = new ArrayList<>();
        if (!icon.saint().isBlank()) {
            lore.add(locale.raw("item.saint", "saint", icon.saint()));
        }
        if (!icon.feast().isBlank()) {
            lore.add(locale.raw("item.feast", "feast", icon.feast()));
        }
        lore.add(locale.raw("item.category", "category", icon.category()));
        if (cfg.showProvider()) {
            lore.add(locale.raw("item.provider", "provider", icon.providerId()));
        }
        if (cfg.showLicense()) {
            lore.add(locale.raw("item.license", "license", icon.license().name()));
        }
        if (!icon.sourceUrl().isBlank()) {
            lore.add(locale.raw("item.source", "source", icon.sourceUrl()));
        }
        if (!icon.description().isBlank()) {
            lore.add("");
            for (String line : Text.wrap(icon.description(), cfg.descriptionWrap())) {
                lore.add(Text.color("&7" + line));
            }
        }
        lore.add("");
        lore.add(locale.raw("item.hint"));
        return lore;
    }

    private static Material resolveMaterial(PluginConfig cfg) {
        Material material = Material.matchMaterial(cfg.itemMaterial());
        return material == null ? Material.FILLED_MAP : material;
    }
}
