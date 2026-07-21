package com.orthodoxicons;

import com.orthodoxicons.fetch.FetchResult;
import com.orthodoxicons.model.Icon;
import com.orthodoxicons.provider.IconProvider;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Stable, public developer API for OrthodoxIcons. Third-party plugins retrieve
 * an instance via {@code OrthodoxIconsPlugin#api()} and use only these methods,
 * insulating them from internal changes. All methods are safe to call from the
 * main thread; long-running operations return {@link CompletableFuture}s.
 */
public interface OrthodoxIconsApi {

    /**
     * @return an immutable snapshot of all stored icons
     */
    List<Icon> allIcons();

    /**
     * @param id icon id
     * @return the icon, if present
     */
    Optional<Icon> icon(UUID id);

    /**
     * Case-insensitive search across name, saint, feast, category and tags.
     *
     * @param query search text
     * @return matching icons
     */
    List<Icon> search(String query);

    /**
     * Builds a decorated, PDC-tagged item stack for an icon.
     *
     * @param icon   the icon
     * @param amount stack size
     * @return the item
     */
    ItemStack createItem(Icon icon, int amount);

    /**
     * Gives an icon to a player, dropping any overflow.
     *
     * @param player the recipient
     * @param icon   the icon
     * @param amount stack size
     */
    void giveIcon(Player player, Icon icon, int amount);

    /**
     * Reads the icon id stored in an item's PDC.
     *
     * @param item candidate item
     * @return the icon id, if the item is an OrthodoxIcons item
     */
    Optional<UUID> readIconId(ItemStack item);

    /**
     * Registers a new icon provider at runtime without modifying plugin code.
     *
     * @param provider the provider to register
     */
    void registerProvider(IconProvider provider);

    /**
     * Triggers an asynchronous fetch/update pass across enabled providers.
     *
     * @return a future with the aggregated result
     */
    CompletableFuture<FetchResult> triggerUpdate();
}
