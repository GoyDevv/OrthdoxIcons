package com.orthodoxicons;

import com.orthodoxicons.cache.CacheManager;
import com.orthodoxicons.command.IconsCommand;
import com.orthodoxicons.command.IconsTabCompleter;
import com.orthodoxicons.compat.CompatManager;
import com.orthodoxicons.config.ConfigManager;
import com.orthodoxicons.craft.CraftListener;
import com.orthodoxicons.craft.RecipeManager;
import com.orthodoxicons.fetch.FetchResult;
import com.orthodoxicons.fetch.IconFetchService;
import com.orthodoxicons.gui.GuiListener;
import com.orthodoxicons.gui.GuiManager;
import com.orthodoxicons.gui.PlayerSessionListener;
import com.orthodoxicons.gui.SearchPrompt;
import com.orthodoxicons.http.HttpClientFactory;
import com.orthodoxicons.http.HttpService;
import com.orthodoxicons.http.RobotsTxtChecker;
import com.orthodoxicons.image.ImageProcessor;
import com.orthodoxicons.item.IconItemFactory;
import com.orthodoxicons.item.IconItemKeys;
import com.orthodoxicons.model.Icon;
import com.orthodoxicons.placement.PlacementListener;
import com.orthodoxicons.placement.PlacementService;
import com.orthodoxicons.placement.WorldListener;
import com.orthodoxicons.provider.CuratedProvider;
import com.orthodoxicons.provider.IconProvider;
import com.orthodoxicons.provider.ProviderRegistry;
import com.orthodoxicons.provider.WikimediaCommonsProvider;
import com.orthodoxicons.storage.Database;
import com.orthodoxicons.storage.FavoritesRepository;
import com.orthodoxicons.storage.IconRepository;
import com.orthodoxicons.storage.PlacedIconRepository;
import com.orthodoxicons.storage.SqliteDatabase;
import com.orthodoxicons.util.Scheduler;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Main plugin entry point and composition root. Performs manual dependency
 * injection (wiring the service graph) in {@link #onEnable()} and cleanly tears
 * everything down in {@link #onDisable()}. All heavy work is delegated to the
 * services; this class only orchestrates lifecycle.
 */
public final class OrthodoxIconsPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private com.orthodoxicons.locale.LocaleManager localeManager;
    private Scheduler scheduler;
    private IconItemKeys itemKeys;
    private CacheManager cacheManager;
    private ImageProcessor imageProcessor;
    private IconItemFactory itemFactory;

    private ExecutorService dbExecutor;
    private Database database;
    private IconRepository iconRepository;
    private PlacedIconRepository placedRepository;
    private FavoritesRepository favoritesRepository;

    private HttpClientFactory httpClientFactory;
    private RobotsTxtChecker robotsChecker;
    private HttpService httpService;
    private ProviderRegistry providerRegistry;
    private IconFetchService fetchService;

    private RecipeManager recipeManager;
    private CompatManager compatManager;
    private PlacementService placementService;
    private GuiManager guiManager;
    private SearchPrompt searchPrompt;
    private GuiListener guiListener;

    private BukkitTask refreshTask;
    private OrthodoxIconsApi api;

    @Override
    public void onEnable() {
        // --- Configuration + localization -------------------------------------
        this.configManager = new ConfigManager(this);
        this.configManager.load();
        this.localeManager = new com.orthodoxicons.locale.LocaleManager(this, configManager);
        this.localeManager.load();
        this.scheduler = new Scheduler(this);

        // --- Items / cache / images ------------------------------------------
        this.itemKeys = new IconItemKeys(this);
        this.cacheManager = new CacheManager(getDataFolder(), getLogger());
        this.cacheManager.initialize();
        this.imageProcessor = new ImageProcessor();
        this.itemFactory = new IconItemFactory(configManager, localeManager, cacheManager,
                imageProcessor, itemKeys);

        // --- Storage ----------------------------------------------------------
        this.dbExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread t = new Thread(runnable, "OrthodoxIcons-DB");
            t.setDaemon(true);
            return t;
        });
        if (!setupDatabase()) {
            getLogger().severe("Storage initialization failed; disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.iconRepository = new IconRepository(database, dbExecutor, getLogger());
        this.placedRepository = new PlacedIconRepository(database, dbExecutor, getLogger());
        this.favoritesRepository = new FavoritesRepository(database, dbExecutor, getLogger());
        // Warm in-memory caches before serving any request.
        CompletableFuture.allOf(
                iconRepository.warmCache(),
                placedRepository.warmCache(),
                favoritesRepository.warmCache()).join();

        // --- HTTP + providers + fetch ----------------------------------------
        this.httpClientFactory = new HttpClientFactory(configManager.get());
        this.robotsChecker = new RobotsTxtChecker(httpClientFactory);
        this.httpService = new HttpService(httpClientFactory, configManager, robotsChecker, getLogger());
        this.providerRegistry = new ProviderRegistry();
        registerBuiltinProviders();
        this.fetchService = new IconFetchService(configManager, providerRegistry, httpService,
                cacheManager, imageProcessor, iconRepository, getLogger());

        // --- Crafting / compatibility / placement ----------------------------
        this.recipeManager = new RecipeManager(this, configManager, itemKeys, getLogger());
        this.recipeManager.reload();
        this.compatManager = new CompatManager(getLogger());
        this.compatManager.detect();
        this.placementService = new PlacementService(configManager, itemFactory, itemKeys,
                iconRepository, placedRepository, compatManager);

        // --- GUI --------------------------------------------------------------
        this.guiManager = new GuiManager(configManager, localeManager, iconRepository,
                favoritesRepository, itemFactory);
        this.searchPrompt = new SearchPrompt(this, configManager, localeManager);
        this.searchPrompt.setGui(guiManager);
        this.guiListener = new GuiListener(guiManager, iconRepository, favoritesRepository,
                itemFactory, localeManager, configManager, searchPrompt);

        registerListeners();
        registerCommand();

        this.api = new ApiImpl();

        scheduleBackgroundWork();

        getLogger().info("OrthodoxIcons enabled with " + iconRepository.count()
                + " icons and " + providerRegistry.size() + " providers.");
    }

    @Override
    public void onDisable() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        if (recipeManager != null) {
            recipeManager.unregisterAll();
        }
        if (httpClientFactory != null) {
            httpClientFactory.close();
        }
        if (dbExecutor != null) {
            dbExecutor.shutdown();
        }
        if (database != null) {
            database.close();
        }
        getLogger().info("OrthodoxIcons disabled.");
    }

    private boolean setupDatabase() {
        String type = configManager.get().storageType();
        // Only SQLITE ships today; the abstraction allows future MySQL wiring.
        if (!"SQLITE".equals(type)) {
            getLogger().warning("Unsupported storage type '" + type + "'; falling back to SQLITE.");
        }
        File dbFile = new File(getDataFolder(), configManager.get().sqliteFile());
        this.database = new SqliteDatabase(dbFile, getLogger());
        try {
            database.initialize();
            return true;
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Could not initialize SQLite storage", e);
            return false;
        }
    }

    private void registerBuiltinProviders() {
        providerRegistry.clear();
        providerRegistry.register(new CuratedProvider(configManager, getDataFolder()));
        providerRegistry.register(new WikimediaCommonsProvider(configManager, httpService, getLogger()));
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(guiListener, this);
        pm.registerEvents(searchPrompt, this);
        pm.registerEvents(new PlayerSessionListener(searchPrompt), this);
        pm.registerEvents(new PlacementListener(placementService, itemFactory, itemKeys,
                placedRepository, localeManager, configManager), this);
        pm.registerEvents(new WorldListener(placedRepository, placementService, itemKeys), this);
        pm.registerEvents(new CraftListener(recipeManager, guiManager), this);
    }

    private void registerCommand() {
        IconsCommand executor = new IconsCommand(this, configManager, localeManager, iconRepository,
                placedRepository, providerRegistry, fetchService, cacheManager, guiManager,
                itemFactory, scheduler);
        IconsTabCompleter completer = new IconsTabCompleter(iconRepository, providerRegistry);
        PluginCommand command = getCommand("icons");
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(completer);
        } else {
            getLogger().severe("Command 'icons' missing from plugin.yml.");
        }
    }

    private void scheduleBackgroundWork() {
        if (configManager.get().fetchOnStartup()) {
            // Delay slightly so all worlds are loaded before map creation.
            scheduler.runSync(() -> { }).thenRun(() ->
                    fetchService.runUpdate().whenComplete((result, error) -> onFetchComplete(result, error)));
        }
        int minutes = configManager.get().refreshIntervalMinutes();
        if (minutes > 0) {
            long periodTicks = minutes * 60L * 20L;
            this.refreshTask = scheduler.repeatingAsync(() -> {
                if (!fetchService.isRunning()) {
                    fetchService.runUpdate().whenComplete(this::onFetchComplete);
                }
            }, periodTicks, periodTicks);
        }
    }

    private void onFetchComplete(FetchResult result, Throwable error) {
        if (error != null) {
            getLogger().log(Level.WARNING, "Scheduled fetch failed", error);
            return;
        }
        // Auto-cleanup runs off the main thread; GUI refresh runs on it.
        if (configManager.get().cacheAutoCleanup()) {
            scheduler.async(() -> {
                Set<UUID> live = new HashSet<>();
                iconRepository.all().forEach(i -> live.add(i.id()));
                Set<UUID> placedIds = new HashSet<>();
                placedRepository.all().forEach(p -> placedIds.add(p.iconId()));
                cacheManager.cleanupOrphans(live);
                cacheManager.enforceSizeLimit(
                        configManager.get().cacheMaxSizeMb() * 1024L * 1024L, placedIds);
                return null;
            });
        }
        onIconsUpdated();
    }

    /**
     * Reloads configuration, localization, recipes, compatibility detection and
     * providers without a server restart. Safe to call from the main thread.
     */
    public void reloadEverything() {
        configManager.reload();
        localeManager.load();
        recipeManager.reload();
        compatManager.detect();
        robotsChecker.clear();
        registerBuiltinProviders();
    }

    /**
     * Refreshes any open browsers so newly-fetched icons appear immediately.
     * Ensures execution on the main thread.
     */
    public void onIconsUpdated() {
        scheduler.runSync(() -> guiListener.refreshOpenBrowsers());
    }

    /**
     * @return the public developer API for third-party integrations
     */
    public OrthodoxIconsApi api() {
        return api;
    }

    /**
     * Concrete {@link OrthodoxIconsApi} implementation backed by the live
     * service graph.
     */
    private final class ApiImpl implements OrthodoxIconsApi {
        @Override
        public List<Icon> allIcons() {
            return iconRepository.all();
        }

        @Override
        public Optional<Icon> icon(UUID id) {
            return iconRepository.find(id);
        }

        @Override
        public List<Icon> search(String query) {
            return iconRepository.search(query);
        }

        @Override
        public ItemStack createItem(Icon icon, int amount) {
            return itemFactory.create(icon, amount);
        }

        @Override
        public void giveIcon(Player player, Icon icon, int amount) {
            player.getInventory().addItem(itemFactory.create(icon, amount))
                    .values().forEach(rest -> player.getWorld()
                            .dropItemNaturally(player.getLocation(), rest));
        }

        @Override
        public Optional<UUID> readIconId(ItemStack item) {
            return itemFactory.readIconId(item);
        }

        @Override
        public void registerProvider(IconProvider provider) {
            providerRegistry.register(provider);
        }

        @Override
        public CompletableFuture<FetchResult> triggerUpdate() {
            return fetchService.runUpdate();
        }
    }
}
