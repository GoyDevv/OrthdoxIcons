package com.orthodoxicons.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;

/**
 * Strongly-typed, immutable snapshot of {@code config.yml}. A fresh instance is
 * produced on every reload so running tasks keep operating against a consistent
 * view of the configuration.
 */
public final class PluginConfig {

    /** How a source image is fitted onto the square map canvas. */
    public enum FitMode { CONTAIN, COVER, STRETCH }

    private final boolean debug;
    private final String locale;

    private final String storageType;
    private final String sqliteFile;

    private final boolean fetchOnStartup;
    private final int refreshIntervalMinutes;
    private final int maxConcurrentDownloads;
    private final int timeoutSeconds;
    private final int retryLimit;
    private final long retryBackoffMs;
    private final boolean respectRobots;
    private final String userAgent;
    private final int maxIconsPerProvider;

    private final boolean cacheAutoCleanup;
    private final int cacheMaxAgeDays;
    private final long cacheMaxSizeMb;

    private final int textureSize;
    private final int thumbnailSize;
    private final FitMode fitMode;
    private final int backgroundColor;

    private final String itemMaterial;
    private final boolean showLicense;
    private final boolean showProvider;
    private final int descriptionWrap;

    private final boolean craftingEnabled;
    private final boolean blankRecipeEnabled;
    private final int blankRecipeAmount;
    private final List<String> blankRecipeShape;
    private final ConfigurationSection blankRecipeIngredients;

    private final boolean placementEnabled;
    private final boolean invisibleFrame;
    private final boolean fixedFrame;
    private final boolean respectProtection;
    private final boolean dropOnBreak;
    private final int maxPerPlayer;

    private final int guiRows;
    private final String guiTitle;
    private final String defaultSort;
    private final boolean anvilSearch;

    private final ConfigurationSection providers;

    /**
     * Reads all values from the given configuration.
     *
     * @param c the loaded {@link FileConfiguration}
     */
    public PluginConfig(FileConfiguration c) {
        this.debug = c.getBoolean("debug", false);
        this.locale = c.getString("locale", "en");

        this.storageType = c.getString("storage.type", "SQLITE").toUpperCase(Locale.ROOT);
        this.sqliteFile = c.getString("storage.sqlite.file", "orthodoxicons.db");

        this.fetchOnStartup = c.getBoolean("fetching.fetch-on-startup", true);
        this.refreshIntervalMinutes = Math.max(0, c.getInt("fetching.refresh-interval-minutes", 720));
        this.maxConcurrentDownloads = Math.max(1, c.getInt("fetching.max-concurrent-downloads", 4));
        this.timeoutSeconds = Math.max(1, c.getInt("fetching.timeout-seconds", 20));
        this.retryLimit = Math.max(0, c.getInt("fetching.retry-limit", 3));
        this.retryBackoffMs = Math.max(0L, c.getLong("fetching.retry-backoff-ms", 750L));
        this.respectRobots = c.getBoolean("fetching.respect-robots-txt", true);
        this.userAgent = c.getString("fetching.user-agent", "OrthodoxIcons/1.0");
        this.maxIconsPerProvider = Math.max(1, c.getInt("fetching.max-icons-per-provider", 250));

        this.cacheAutoCleanup = c.getBoolean("cache.auto-cleanup", true);
        this.cacheMaxAgeDays = Math.max(0, c.getInt("cache.max-age-days", 0));
        this.cacheMaxSizeMb = Math.max(0L, c.getLong("cache.max-size-mb", 512L));

        this.textureSize = clamp(c.getInt("image.texture-size", 128), 16, 128);
        this.thumbnailSize = clamp(c.getInt("image.thumbnail-size", 64), 8, 128);
        this.fitMode = parseFit(c.getString("image.fit-mode", "CONTAIN"));
        this.backgroundColor = parseColor(c.getString("image.background-color", "#00000000"));

        this.itemMaterial = c.getString("item.material", "FILLED_MAP").toUpperCase(Locale.ROOT);
        this.showLicense = c.getBoolean("item.show-license", true);
        this.showProvider = c.getBoolean("item.show-provider", true);
        this.descriptionWrap = Math.max(16, c.getInt("item.description-wrap", 40));

        this.craftingEnabled = c.getBoolean("crafting.enabled", true);
        this.blankRecipeEnabled = c.getBoolean("crafting.blank-icon.enabled", true);
        this.blankRecipeAmount = Math.max(1, c.getInt("crafting.blank-icon.result-amount", 1));
        this.blankRecipeShape = c.getStringList("crafting.blank-icon.shape");
        this.blankRecipeIngredients = c.getConfigurationSection("crafting.blank-icon.ingredients");

        this.placementEnabled = c.getBoolean("placement.enabled", true);
        this.invisibleFrame = c.getBoolean("placement.invisible-frame", true);
        this.fixedFrame = c.getBoolean("placement.fixed-frame", true);
        this.respectProtection = c.getBoolean("placement.respect-protection", true);
        this.dropOnBreak = c.getBoolean("placement.drop-on-break", true);
        this.maxPerPlayer = Math.max(0, c.getInt("placement.max-per-player", 0));

        this.guiRows = clamp(c.getInt("gui.rows", 6), 3, 6);
        this.guiTitle = c.getString("gui.title", "&5&lOrthodox Icons");
        this.defaultSort = c.getString("gui.default-sort", "NAME").toUpperCase(Locale.ROOT);
        this.anvilSearch = c.getBoolean("gui.anvil-search", true);

        this.providers = c.getConfigurationSection("providers");
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static FitMode parseFit(String raw) {
        try {
            return FitMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return FitMode.CONTAIN;
        }
    }

    private static int parseColor(String hex) {
        if (hex == null) return 0;
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            long value = Long.parseLong(h, 16);
            if (h.length() <= 6) {
                // No alpha specified; treat as fully transparent background.
                return (int) (value & 0xFFFFFF);
            }
            return (int) value;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    public boolean debug() { return debug; }
    public String locale() { return locale; }
    public String storageType() { return storageType; }
    public String sqliteFile() { return sqliteFile; }
    public boolean fetchOnStartup() { return fetchOnStartup; }
    public int refreshIntervalMinutes() { return refreshIntervalMinutes; }
    public int maxConcurrentDownloads() { return maxConcurrentDownloads; }
    public int timeoutSeconds() { return timeoutSeconds; }
    public int retryLimit() { return retryLimit; }
    public long retryBackoffMs() { return retryBackoffMs; }
    public boolean respectRobots() { return respectRobots; }
    public String userAgent() { return userAgent; }
    public int maxIconsPerProvider() { return maxIconsPerProvider; }
    public boolean cacheAutoCleanup() { return cacheAutoCleanup; }
    public int cacheMaxAgeDays() { return cacheMaxAgeDays; }
    public long cacheMaxSizeMb() { return cacheMaxSizeMb; }
    public int textureSize() { return textureSize; }
    public int thumbnailSize() { return thumbnailSize; }
    public FitMode fitMode() { return fitMode; }
    public int backgroundColor() { return backgroundColor; }
    public String itemMaterial() { return itemMaterial; }
    public boolean showLicense() { return showLicense; }
    public boolean showProvider() { return showProvider; }
    public int descriptionWrap() { return descriptionWrap; }
    public boolean craftingEnabled() { return craftingEnabled; }
    public boolean blankRecipeEnabled() { return blankRecipeEnabled; }
    public int blankRecipeAmount() { return blankRecipeAmount; }
    public List<String> blankRecipeShape() { return blankRecipeShape; }
    public ConfigurationSection blankRecipeIngredients() { return blankRecipeIngredients; }
    public boolean placementEnabled() { return placementEnabled; }
    public boolean invisibleFrame() { return invisibleFrame; }
    public boolean fixedFrame() { return fixedFrame; }
    public boolean respectProtection() { return respectProtection; }
    public boolean dropOnBreak() { return dropOnBreak; }
    public int maxPerPlayer() { return maxPerPlayer; }
    public int guiRows() { return guiRows; }
    public String guiTitle() { return guiTitle; }
    public String defaultSort() { return defaultSort; }
    public boolean anvilSearch() { return anvilSearch; }
    public ConfigurationSection providers() { return providers; }
}
