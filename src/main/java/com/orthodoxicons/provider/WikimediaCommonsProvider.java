package com.orthodoxicons.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.orthodoxicons.config.ConfigManager;
import com.orthodoxicons.config.PluginConfig;
import com.orthodoxicons.http.HttpService;
import com.orthodoxicons.model.IconMetadata;
import com.orthodoxicons.model.License;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Crawls Wikimedia Commons categories via the public MediaWiki API and returns
 * metadata for freely-licensed files only. Uses {@code imageinfo} with
 * {@code extmetadata} to read the license, then filters against the configured
 * allow-list. Respects robots.txt through {@link HttpService}.
 */
public final class WikimediaCommonsProvider implements IconProvider {

    /** Stable provider id. */
    public static final String ID = "wikimedia";

    private static final String API = "https://commons.wikimedia.org/w/api.php";

    private final ConfigManager config;
    private final HttpService http;
    private final Logger logger;

    /**
     * @param config configuration manager
     * @param http   HTTP service
     * @param logger plugin logger
     */
    public WikimediaCommonsProvider(ConfigManager config, HttpService http, Logger logger) {
        this.config = config;
        this.http = http;
        this.logger = logger;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Wikimedia Commons";
    }

    @Override
    public boolean isEnabled() {
        var providers = config.get().providers();
        return providers != null && providers.getBoolean(ID + ".enabled", false);
    }

    @Override
    public List<IconMetadata> discover() throws Exception {
        List<IconMetadata> result = new ArrayList<>();
        PluginConfig cfg = config.get();
        var section = cfg.providers();
        if (section == null) {
            return result;
        }
        List<String> categories = section.getStringList(ID + ".categories");
        List<String> allowed = section.getStringList(ID + ".allowed-licenses").stream()
                .map(s -> s.toLowerCase(Locale.ROOT)).toList();
        int perProvider = cfg.maxIconsPerProvider();

        for (String category : categories) {
            if (result.size() >= perProvider) {
                break;
            }
            crawlCategory(category, allowed, result, perProvider);
        }
        return result;
    }

    private void crawlCategory(String category, List<String> allowed,
                               List<IconMetadata> out, int limit) {
        String cmcontinue = null;
        int guard = 0;
        do {
            if (out.size() >= limit || guard++ > 20) {
                return;
            }
            StringBuilder url = new StringBuilder(API)
                    .append("?action=query&format=json&generator=categorymembers")
                    .append("&gcmtype=file&gcmlimit=50&gcmtitle=Category:")
                    .append(encode(category))
                    .append("&prop=imageinfo&iiprop=url|extmetadata|mime|size")
                    .append("&iiurlwidth=512");
            if (cmcontinue != null) {
                url.append("&gcmcontinue=").append(encode(cmcontinue));
            }
            try {
                String body = http.getText(url.toString());
                JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                cmcontinue = extractContinue(root);
                parsePages(root, category, allowed, out, limit);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Wikimedia crawl failed for category '"
                        + category + "': " + e.getMessage());
                return;
            }
        } while (cmcontinue != null);
    }

    private void parsePages(JsonObject root, String category, List<String> allowed,
                            List<IconMetadata> out, int limit) {
        if (!root.has("query")) {
            return;
        }
        JsonObject query = root.getAsJsonObject("query");
        if (!query.has("pages")) {
            return;
        }
        JsonObject pages = query.getAsJsonObject("pages");
        for (var entry : pages.entrySet()) {
            if (out.size() >= limit) {
                return;
            }
            JsonObject page = entry.getValue().getAsJsonObject();
            String title = page.has("title") ? page.get("title").getAsString() : "";
            if (!page.has("imageinfo")) {
                continue;
            }
            JsonArray infoArr = page.getAsJsonArray("imageinfo");
            if (infoArr.isEmpty()) {
                continue;
            }
            JsonObject info = infoArr.get(0).getAsJsonObject();
            String mime = info.has("mime") ? info.get("mime").getAsString() : "";
            if (!mime.startsWith("image/")) {
                continue;
            }
            JsonObject meta = info.has("extmetadata") ? info.getAsJsonObject("extmetadata") : new JsonObject();
            String licenseLabel = metaValue(meta, "LicenseShortName");
            if (licenseLabel.isBlank()) {
                licenseLabel = metaValue(meta, "License");
            }
            if (!isAllowed(licenseLabel, allowed)) {
                continue;
            }
            String imageUrl = info.has("thumburl") ? info.get("thumburl").getAsString()
                    : (info.has("url") ? info.get("url").getAsString() : "");
            if (imageUrl.isBlank()) {
                continue;
            }
            String sourceUrl = info.has("descriptionurl") ? info.get("descriptionurl").getAsString() : "";
            String artist = stripHtml(metaValue(meta, "Artist"));
            String cleanTitle = title.replaceFirst("^File:", "").replaceAll("\\.[A-Za-z0-9]+$", "");

            out.add(IconMetadata.builder()
                    .providerId(ID)
                    .name(cleanTitle)
                    .saint("")
                    .feast("")
                    .category(category)
                    .description(stripHtml(metaValue(meta, "ImageDescription")))
                    .imageUrl(imageUrl)
                    .sourceUrl(sourceUrl)
                    .attribution(artist.isBlank() ? "Wikimedia Commons" : artist)
                    .license(License.fromLabel(licenseLabel))
                    .tags(List.of("wikimedia", category.toLowerCase(Locale.ROOT)))
                    .build());
        }
    }

    private static boolean isAllowed(String licenseLabel, List<String> allowed) {
        if (allowed.isEmpty()) {
            return License.fromLabel(licenseLabel).isFree();
        }
        String lower = licenseLabel.toLowerCase(Locale.ROOT);
        return allowed.stream().anyMatch(lower::contains);
    }

    private static String extractContinue(JsonObject root) {
        if (root.has("continue")) {
            JsonObject cont = root.getAsJsonObject("continue");
            if (cont.has("gcmcontinue")) {
                return cont.get("gcmcontinue").getAsString();
            }
        }
        return null;
    }

    private static String metaValue(JsonObject meta, String key) {
        if (meta.has(key)) {
            JsonElement el = meta.get(key);
            if (el.isJsonObject() && el.getAsJsonObject().has("value")) {
                return el.getAsJsonObject().get("value").getAsString();
            }
        }
        return "";
    }

    private static String stripHtml(String value) {
        return value == null ? "" : value.replaceAll("<[^>]+>", "").trim();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
