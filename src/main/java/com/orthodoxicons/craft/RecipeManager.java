package com.orthodoxicons.craft;

import com.orthodoxicons.config.ConfigManager;
import com.orthodoxicons.config.PluginConfig;
import com.orthodoxicons.item.IconItemKeys;
import com.orthodoxicons.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Registers and reloads the plugin's crafting recipes without a server restart.
 * The bundled recipe crafts a "blank" consecration item that a player fills via
 * the GUI; the shape and ingredients are fully configurable. All recipe keys are
 * namespaced so they can be cleanly removed and re-registered on reload.
 */
public final class RecipeManager {

    private final Plugin plugin;
    private final ConfigManager config;
    private final IconItemKeys keys;
    private final Logger logger;
    private final List<NamespacedKey> registered = new ArrayList<>();
    private final NamespacedKey blankKey;

    /**
     * @param plugin owning plugin
     * @param config configuration manager
     * @param keys   namespaced keys
     * @param logger plugin logger
     */
    public RecipeManager(Plugin plugin, ConfigManager config, IconItemKeys keys, Logger logger) {
        this.plugin = plugin;
        this.config = config;
        this.keys = keys;
        this.logger = logger;
        this.blankKey = new NamespacedKey(plugin, "blank_icon");
    }

    /**
     * @return the namespaced key identifying the blank-icon marker
     */
    public NamespacedKey blankKey() {
        return blankKey;
    }

    /**
     * Removes any previously-registered recipes and re-registers them from the
     * current configuration. Safe to call repeatedly (e.g. on /icons reload).
     */
    public void reload() {
        unregisterAll();
        PluginConfig cfg = config.get();
        if (!cfg.craftingEnabled()) {
            logger.info("Crafting disabled; no recipes registered.");
            return;
        }
        if (cfg.blankRecipeEnabled()) {
            registerBlankRecipe(cfg);
        }
    }

    private void registerBlankRecipe(PluginConfig cfg) {
        List<String> shape = cfg.blankRecipeShape();
        ConfigurationSection ingredients = cfg.blankRecipeIngredients();
        if (shape == null || shape.isEmpty() || ingredients == null) {
            logger.warning("Blank-icon recipe is enabled but shape/ingredients are missing.");
            return;
        }
        ItemStack result = createBlankItem(cfg.blankRecipeAmount());
        ShapedRecipe recipe = new ShapedRecipe(blankKey, result);
        try {
            recipe.shape(shape.toArray(new String[0]));
            for (String symbolKey : ingredients.getKeys(false)) {
                if (symbolKey.length() != 1) {
                    logger.warning("Ingredient key '" + symbolKey + "' must be a single character.");
                    continue;
                }
                String materialName = ingredients.getString(symbolKey, "");
                Material material = Material.matchMaterial(materialName);
                if (material == null) {
                    logger.warning("Unknown ingredient material '" + materialName
                            + "' for symbol '" + symbolKey + "'.");
                    continue;
                }
                recipe.setIngredient(symbolKey.charAt(0), material);
            }
            Bukkit.addRecipe(recipe);
            registered.add(blankKey);
            logger.info("Registered blank-icon crafting recipe.");
        } catch (IllegalArgumentException e) {
            logger.warning("Failed to register blank-icon recipe: " + e.getMessage());
        }
    }

    /**
     * Creates the blank consecration item produced by the recipe.
     *
     * @param amount stack size
     * @return the blank item, tagged in PDC
     */
    public ItemStack createBlankItem(int amount) {
        ItemStack item = new ItemStack(Material.PAINTING, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&d&lBlank Icon Panel"));
            meta.setLore(List.of(
                    Text.color("&7Open the icon browser and"),
                    Text.color("&7select an icon to consecrate."),
                    Text.color("&8Right-click while holding to browse.")));
            meta.getPersistentDataContainer().set(blankKey, PersistentDataType.INTEGER, 1);
            meta.getPersistentDataContainer().set(keys.marker(), PersistentDataType.INTEGER, 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * @param item candidate
     * @return whether the item is a blank icon panel
     */
    public boolean isBlankItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Integer flag = item.getItemMeta().getPersistentDataContainer()
                .get(blankKey, PersistentDataType.INTEGER);
        return flag != null && flag == 1;
    }

    /**
     * Unregisters all recipes this manager added.
     */
    public void unregisterAll() {
        for (NamespacedKey key : registered) {
            Bukkit.removeRecipe(key);
        }
        registered.clear();
    }
}
