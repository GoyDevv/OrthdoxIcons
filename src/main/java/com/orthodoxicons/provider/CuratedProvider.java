package com.orthodoxicons.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.orthodoxicons.config.ConfigManager;
import com.orthodoxicons.model.IconMetadata;
import com.orthodoxicons.model.License;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Provider backed by the bundled {@code curated-icons.json} manifest. This
 * provider needs no network access to enumerate icons (only the images are
 * downloaded by the fetch service), guaranteeing a working baseline set even
 * when crawler providers are disabled or unreachable.
 */
public final class CuratedProvider implements IconProvider {

    /** Stable provider id. */
    public static final String ID = "curated";

    private final ConfigManager config;
    private final File manifestFile;

    /**
     * @param config       configuration manager
     * @param dataFolder   plugin data folder holding the manifest
     */
    public CuratedProvider(ConfigManager config, File dataFolder) {
        this.config = config;
        this.manifestFile = new File(dataFolder, "curated-icons.json");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Curated Public-Domain Manifest";
    }

    @Override
    public boolean isEnabled() {
        var providers = config.get().providers();
        return providers != null && providers.getBoolean(ID + ".enabled", true);
    }

    @Override
    public List<IconMetadata> discover() throws IOException {
        List<IconMetadata> result = new ArrayList<>();
        if (!manifestFile.exists()) {
            return result;
        }
        String json = Files.readString(manifestFile.toPath(), StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("icons")) {
            return result;
        }
        JsonArray icons = root.getAsJsonArray("icons");
        for (JsonElement element : icons) {
            JsonObject o = element.getAsJsonObject();
            List<String> tags = new ArrayList<>();
            if (o.has("tags") && o.get("tags").isJsonArray()) {
                for (JsonElement t : o.getAsJsonArray("tags")) {
                    tags.add(t.getAsString());
                }
            }
            result.add(IconMetadata.builder()
                    .providerId(ID)
                    .name(str(o, "name"))
                    .saint(str(o, "saint"))
                    .feast(str(o, "feast"))
                    .category(str(o, "category"))
                    .description(str(o, "description"))
                    .imageUrl(str(o, "imageUrl"))
                    .sourceUrl(str(o, "sourceUrl"))
                    .attribution(str(o, "attribution"))
                    .license(License.fromLabel(str(o, "license")))
                    .tags(tags)
                    .build());
        }
        return result;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }
}
