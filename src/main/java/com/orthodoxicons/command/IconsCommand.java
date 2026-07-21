package com.orthodoxicons.command;

import com.orthodoxicons.OrthodoxIconsPlugin;
import com.orthodoxicons.cache.CacheManager;
import com.orthodoxicons.config.ConfigManager;
import com.orthodoxicons.fetch.FetchResult;
import com.orthodoxicons.fetch.IconFetchService;
import com.orthodoxicons.gui.GuiManager;
import com.orthodoxicons.item.IconItemFactory;
import com.orthodoxicons.locale.LocaleManager;
import com.orthodoxicons.model.Icon;
import com.orthodoxicons.provider.IconProvider;
import com.orthodoxicons.provider.ProviderRegistry;
import com.orthodoxicons.storage.IconRepository;
import com.orthodoxicons.storage.PlacedIconRepository;
import com.orthodoxicons.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Root {@code /icons} command executor. Dispatches to subcommands, each guarded
 * by its own permission. All potentially-blocking work is delegated to async
 * services; the command handler itself never performs I/O on the main thread.
 */
public final class IconsCommand implements CommandExecutor {

    private final OrthodoxIconsPlugin plugin;
    private final ConfigManager config;
    private final LocaleManager locale;
    private final IconRepository icons;
    private final PlacedIconRepository placed;
    private final ProviderRegistry providers;
    private final IconFetchService fetch;
    private final CacheManager cache;
    private final GuiManager gui;
    private final IconItemFactory itemFactory;
    private final Scheduler scheduler;
    private final Random random = new Random();

    /**
     * @param plugin      owning plugin
     * @param config      configuration manager
     * @param locale      locale manager
     * @param icons       icon repository
     * @param placed      placed-icon repository
     * @param providers   provider registry
     * @param fetch       fetch service
     * @param cache       cache manager
     * @param gui         gui manager
     * @param itemFactory item factory
     * @param scheduler   scheduler helper
     */
    public IconsCommand(OrthodoxIconsPlugin plugin, ConfigManager config, LocaleManager locale,
                        IconRepository icons, PlacedIconRepository placed, ProviderRegistry providers,
                        IconFetchService fetch, CacheManager cache, GuiManager gui,
                        IconItemFactory itemFactory, Scheduler scheduler) {
        this.plugin = plugin;
        this.config = config;
        this.locale = locale;
        this.icons = icons;
        this.placed = placed;
        this.providers = providers;
        this.fetch = fetch;
        this.cache = cache;
        this.gui = gui;
        this.itemFactory = itemFactory;
        this.scheduler = scheduler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return browse(sender);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> help(sender);
            case "browse" -> browse(sender);
            case "search" -> search(sender, args);
            case "random" -> randomIcon(sender);
            case "give" -> give(sender, args);
            case "info" -> info(sender, args);
            case "reload" -> reload(sender);
            case "update" -> update(sender);
            case "cache" -> cache(sender, args);
            case "provider" -> provider(sender);
            case "debug" -> debug(sender);
            case "stats" -> stats(sender);
            case "version" -> version(sender);
            default -> locale.send(sender, "general.unknown-command");
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(locale.raw("command.help.header"));
        String[][] entries = {
                {"browse", "Open the icon browser"},
                {"search <text>", "Search icons by name/saint/feast/tag"},
                {"random", "Get a random icon"},
                {"give <player> <id> [n]", "Give an icon to a player"},
                {"info <id>", "Show details about an icon"},
                {"reload", "Reload config, language and recipes"},
                {"update", "Fetch new/updated icons now"},
                {"cache <info|clean|clear>", "Manage the local cache"},
                {"provider", "List providers and status"},
                {"debug", "Toggle debug logging"},
                {"stats", "Show plugin statistics"},
                {"version", "Show plugin version"},
        };
        for (String[] e : entries) {
            sender.sendMessage(locale.raw("command.help.line", "usage", e[0], "description", e[1]));
        }
    }

    private boolean browse(CommandSender sender) {
        if (!requirePlayer(sender)) {
            return true;
        }
        if (!requirePermission(sender, "orthodoxicons.use")) {
            return true;
        }
        gui.open((Player) sender);
        return true;
    }

    private void search(CommandSender sender, String[] args) {
        if (!requirePlayer(sender) || !requirePermission(sender, "orthodoxicons.use")) {
            return;
        }
        Player player = (Player) sender;
        if (args.length < 2) {
            gui.open(player);
            return;
        }
        String query = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        List<Icon> results = icons.search(query);
        if (results.isEmpty()) {
            locale.send(sender, "general.icon-not-found", "query", query);
            return;
        }
        gui.open(player);
        // Apply the query to the freshly-opened browser holder.
        if (player.getOpenInventory().getTopInventory().getHolder()
                instanceof com.orthodoxicons.gui.BrowserHolder holder) {
            holder.setSearch(query);
            holder.setPage(0);
            gui.render(player, holder);
        }
    }

    private void randomIcon(CommandSender sender) {
        if (!requirePlayer(sender) || !requirePermission(sender, "orthodoxicons.use")) {
            return;
        }
        List<Icon> all = icons.all();
        if (all.isEmpty()) {
            locale.send(sender, "gui.empty");
            return;
        }
        Player player = (Player) sender;
        Icon icon = all.get(random.nextInt(all.size()));
        player.getInventory().addItem(itemFactory.create(icon, 1))
                .values().forEach(rest -> player.getWorld()
                        .dropItemNaturally(player.getLocation(), rest));
        locale.send(sender, "command.random.given", "icon", icon.name());
    }

    private void give(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "orthodoxicons.give")) {
            return;
        }
        if (args.length < 3) {
            locale.send(sender, "command.give.usage");
            return;
        }
        OfflinePlayer targetOffline = Bukkit.getPlayerExact(args[1]);
        if (!(targetOffline instanceof Player target)) {
            locale.send(sender, "general.player-not-found", "player", args[1]);
            return;
        }
        Optional<Icon> icon = resolveIcon(args[2]);
        if (icon.isEmpty()) {
            locale.send(sender, "general.icon-not-found", "query", args[2]);
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException e) {
                locale.send(sender, "general.invalid-number", "value", args[3]);
                return;
            }
        }
        target.getInventory().addItem(itemFactory.create(icon.get(), amount))
                .values().forEach(rest -> target.getWorld()
                        .dropItemNaturally(target.getLocation(), rest));
        locale.send(sender, "command.give.success",
                "amount", String.valueOf(amount), "icon", icon.get().name(), "player", target.getName());
        locale.send(target, "command.give.received",
                "amount", String.valueOf(amount), "icon", icon.get().name());
    }

    private void info(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "orthodoxicons.use")) {
            return;
        }
        if (args.length < 2) {
            locale.send(sender, "command.info.usage");
            return;
        }
        Optional<Icon> icon = resolveIcon(args[1]);
        if (icon.isEmpty()) {
            locale.send(sender, "general.icon-not-found", "query", args[1]);
            return;
        }
        Icon i = icon.get();
        sender.sendMessage(locale.raw("command.info.header", "title", i.name()));
        if (!i.saint().isBlank()) sender.sendMessage(locale.raw("command.info.saint", "saint", i.saint()));
        if (!i.feast().isBlank()) sender.sendMessage(locale.raw("command.info.feast", "feast", i.feast()));
        sender.sendMessage(locale.raw("command.info.category", "category", i.category()));
        sender.sendMessage(locale.raw("command.info.provider", "provider", i.providerId()));
        sender.sendMessage(locale.raw("command.info.license", "license", i.license().name()));
        sender.sendMessage(locale.raw("command.info.tags", "tags",
                i.tags().isEmpty() ? "-" : String.join(", ", i.tags())));
        if (!i.sourceUrl().isBlank()) {
            sender.sendMessage(locale.raw("command.info.source", "source", i.sourceUrl()));
        }
    }

    private void reload(CommandSender sender) {
        if (!requirePermission(sender, "orthodoxicons.reload")) {
            return;
        }
        locale.send(sender, "general.reloading");
        try {
            plugin.reloadEverything();
            locale.send(sender, "command.reload.success");
        } catch (RuntimeException e) {
            locale.send(sender, "command.reload.failed", "error", String.valueOf(e.getMessage()));
        }
    }

    private void update(CommandSender sender) {
        if (!requirePermission(sender, "orthodoxicons.update")) {
            return;
        }
        if (fetch.isRunning()) {
            locale.send(sender, "general.busy");
            return;
        }
        locale.send(sender, "command.update.started");
        fetch.runUpdate().whenComplete((result, error) -> scheduler.runSync(() -> {
            if (error != null) {
                locale.send(sender, "command.reload.failed", "error", String.valueOf(error.getMessage()));
                return;
            }
            FetchResult r = result == null ? FetchResult.empty() : result;
            locale.send(sender, "command.update.finished",
                    "added", String.valueOf(r.added()), "updated", String.valueOf(r.updated()),
                    "skipped", String.valueOf(r.skipped()), "failed", String.valueOf(r.failed()));
            plugin.onIconsUpdated();
        }));
    }

    private void cache(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "orthodoxicons.cache")) {
            return;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "info";
        switch (action) {
            case "info" -> locale.send(sender, "command.cache.info",
                    "icons", String.valueOf(icons.count()),
                    "images", String.valueOf(cache.imageCount()),
                    "size", humanBytes(cache.totalSize()));
            case "clean" -> scheduler.async(() -> {
                java.util.Set<UUID> live = new java.util.HashSet<>();
                icons.all().forEach(i -> live.add(i.id()));
                int removed = cache.cleanupOrphans(live);
                return removed;
            }).thenAccept(removed -> scheduler.runSync(() ->
                    locale.send(sender, "command.cache.cleaned", "removed", String.valueOf(removed))));
            case "clear" -> scheduler.async(cache::clearAll).thenAccept(removed ->
                    scheduler.runSync(() -> {
                        locale.send(sender, "command.cache.cleared", "removed", String.valueOf(removed));
                        plugin.onIconsUpdated();
                    }));
            default -> locale.send(sender, "command.cache.usage");
        }
    }

    private void provider(CommandSender sender) {
        if (!requirePermission(sender, "orthodoxicons.admin")) {
            return;
        }
        sender.sendMessage(locale.raw("command.provider.header"));
        for (IconProvider p : providers.all()) {
            long count = icons.all().stream().filter(i -> i.providerId().equals(p.id())).count();
            String state = p.isEnabled()
                    ? locale.raw("command.provider.enabled")
                    : locale.raw("command.provider.disabled");
            sender.sendMessage(locale.raw("command.provider.line",
                    "id", p.id(), "state", state, "count", String.valueOf(count)));
        }
    }

    private void debug(CommandSender sender) {
        if (!requirePermission(sender, "orthodoxicons.debug")) {
            return;
        }
        boolean now = config.toggleDebug();
        locale.send(sender, "command.debug.toggled", "state",
                now ? locale.raw("command.debug.on") : locale.raw("command.debug.off"));
    }

    private void stats(CommandSender sender) {
        if (!requirePermission(sender, "orthodoxicons.use")) {
            return;
        }
        locale.send(sender, "command.stats",
                "icons", String.valueOf(icons.count()),
                "placed", String.valueOf(placed.count()),
                "providers", String.valueOf(providers.enabled().size()),
                "cache", humanBytes(cache.totalSize()));
    }

    private void version(CommandSender sender) {
        locale.send(sender, "command.version",
                "version", plugin.getDescription().getVersion(),
                "server", Bukkit.getVersion());
    }

    private Optional<Icon> resolveIcon(String token) {
        try {
            UUID id = UUID.fromString(token);
            return icons.find(id);
        } catch (IllegalArgumentException ignored) {
            // Not a UUID - resolve by exact/first name match.
            return icons.all().stream()
                    .filter(i -> i.name().equalsIgnoreCase(token))
                    .findFirst()
                    .or(() -> icons.search(token).stream().findFirst());
        }
    }

    private boolean requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            locale.send(sender, "general.players-only");
            return false;
        }
        return true;
    }

    private boolean requirePermission(CommandSender sender, String node) {
        if (!sender.hasPermission(node)) {
            locale.send(sender, "general.no-permission");
            return false;
        }
        return true;
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
