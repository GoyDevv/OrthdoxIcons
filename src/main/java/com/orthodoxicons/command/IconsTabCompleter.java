package com.orthodoxicons.command;

import com.orthodoxicons.provider.ProviderRegistry;
import com.orthodoxicons.storage.IconRepository;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Provides tab-completion for {@code /icons}, suggesting subcommands, player
 * names, cache actions and icon names based on the caller's permissions.
 */
public final class IconsTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "help", "browse", "search", "random", "give", "info", "reload",
            "update", "cache", "provider", "debug", "stats", "version");
    private static final List<String> CACHE_ACTIONS = List.of("info", "clean", "clear");

    private final IconRepository icons;
    private final ProviderRegistry providers;

    /**
     * @param icons     icon repository
     * @param providers provider registry
     */
    public IconsTabCompleter(IconRepository icons, ProviderRegistry providers) {
        this.icons = icons;
        this.providers = providers;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], SUBCOMMANDS, completions);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "cache" -> StringUtil.copyPartialMatches(args[1], CACHE_ACTIONS, completions);
                case "give" -> {
                    List<String> names = new ArrayList<>();
                    Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
                    StringUtil.copyPartialMatches(args[1], names, completions);
                }
                case "info" -> StringUtil.copyPartialMatches(args[1], iconNames(), completions);
                default -> { /* no suggestions */ }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            StringUtil.copyPartialMatches(args[2], iconNames(), completions);
        }
        completions.sort(String.CASE_INSENSITIVE_ORDER);
        return completions;
    }

    private List<String> iconNames() {
        List<String> names = new ArrayList<>();
        icons.all().forEach(i -> names.add(i.name().replace(' ', '_')));
        return names;
    }
}
