package com.orthodoxicons.fetch;

import com.orthodoxicons.cache.CacheManager;
import com.orthodoxicons.config.ConfigManager;
import com.orthodoxicons.config.PluginConfig;
import com.orthodoxicons.http.HttpService;
import com.orthodoxicons.image.ImageProcessor;
import com.orthodoxicons.model.Icon;
import com.orthodoxicons.model.IconMetadata;
import com.orthodoxicons.provider.IconProvider;
import com.orthodoxicons.provider.ProviderRegistry;
import com.orthodoxicons.storage.IconRepository;
import com.orthodoxicons.util.Hashing;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service layer that orchestrates discovery, conditional downloading, image
 * processing, hashing and persistence. Every step runs off the main thread. It
 * detects updates via ETag / Last-Modified / hash, skips duplicates and
 * unchanged images, retries via {@link HttpService}, validates and recovers
 * images, and persists results through the {@link IconRepository}.
 */
public final class IconFetchService {

    private final ConfigManager config;
    private final ProviderRegistry registry;
    private final HttpService http;
    private final CacheManager cache;
    private final ImageProcessor images;
    private final IconRepository repository;
    private final Logger logger;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * @param config     configuration manager
     * @param registry   provider registry
     * @param http       HTTP service
     * @param cache      cache manager
     * @param images     image processor
     * @param repository icon repository
     * @param logger     plugin logger
     */
    public IconFetchService(ConfigManager config, ProviderRegistry registry, HttpService http,
                            CacheManager cache, ImageProcessor images,
                            IconRepository repository, Logger logger) {
        this.config = config;
        this.registry = registry;
        this.http = http;
        this.cache = cache;
        this.images = images;
        this.repository = repository;
        this.logger = logger;
    }

    /**
     * @return whether a fetch pass is currently running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Runs a full fetch/update pass across every enabled provider. If a pass is
     * already running the returned future completes immediately with an empty
     * result to avoid overlapping work.
     *
     * @return a future with the aggregated {@link FetchResult}
     */
    public CompletableFuture<FetchResult> runUpdate() {
        if (!running.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(FetchResult.empty());
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return doUpdate();
            } finally {
                running.set(false);
            }
        });
    }

    private FetchResult doUpdate() {
        FetchResult total = FetchResult.empty();
        List<IconProvider> providers = registry.enabled();
        Set<UUID> seen = new HashSet<>();
        for (IconProvider provider : providers) {
            try {
                List<IconMetadata> discovered = provider.discover();
                if (config.debug()) {
                    logger.info("Provider '" + provider.id() + "' discovered "
                            + discovered.size() + " icons.");
                }
                for (IconMetadata meta : discovered) {
                    UUID id = Hashing.stableId(meta.providerId(), stableKey(meta));
                    if (!seen.add(id)) {
                        // Duplicate within this pass - skip.
                        total = total.combine(new FetchResult(0, 0, 1, 0));
                        continue;
                    }
                    total = total.combine(process(id, meta));
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Provider '" + provider.id()
                        + "' failed during discovery: " + e.getMessage());
            }
        }
        logger.info("Fetch pass complete: " + total);
        return total;
    }

    private FetchResult process(UUID id, IconMetadata meta) {
        try {
            // Reject non-free licenses outright.
            if (!meta.license().isFree()) {
                if (config.debug()) {
                    logger.info("Skipping non-free icon '" + meta.name()
                            + "' (" + meta.license() + ")");
                }
                return new FetchResult(0, 0, 1, 0);
            }
            Optional<Icon> existing = repository.find(id);
            String etag = existing.map(Icon::etag).orElse(null);
            String lastMod = existing.map(Icon::lastModified).orElse(null);
            boolean hasImage = cache.hasImage(id);

            HttpService.ConditionalResult result =
                    http.getBinaryConditional(meta.imageUrl(),
                            hasImage ? etag : null, hasImage ? lastMod : null);

            if (!result.isModified() && hasImage) {
                // Unchanged - never re-download. Ensure processed artifacts exist.
                ensureProcessed(id);
                return new FetchResult(0, 0, 1, 0);
            }

            byte[] bytes = result.body();
            if (bytes == null || bytes.length == 0) {
                return new FetchResult(0, 0, 0, 1);
            }

            // Validate + decode; corrupt downloads count as a failure.
            BufferedImage decoded = images.decode(bytes);
            String hash = Hashing.sha256(bytes);

            boolean update = existing.isPresent();
            if (update && hash.equals(existing.get().imageHash()) && hasImage) {
                // Body changed headers but pixels identical - treat as skip but
                // refresh conditional headers for future requests.
                Icon refreshed = existing.get().toBuilder()
                        .etag(result.etag().orElse(etag))
                        .lastModified(result.lastModified().orElse(lastMod))
                        .build();
                repository.save(refreshed);
                ensureProcessed(id);
                return new FetchResult(0, 0, 1, 0);
            }

            // Persist original + processed artifacts.
            cache.writeAtomic(cache.imageFile(id), bytes);
            PluginConfig cfg = config.get();
            images.writePng(images.toTexture(decoded, cfg), cache.processedFile(id));
            images.writePng(images.toThumbnail(decoded, cfg), cache.thumbnailFile(id));

            Icon icon = Icon.builder()
                    .id(id)
                    .fromMetadata(meta)
                    .imageHash(hash)
                    .etag(result.etag().orElse(etag))
                    .lastModified(result.lastModified().orElse(lastMod))
                    .dateAdded(existing.map(Icon::dateAdded).orElse(Instant.now()))
                    .build();
            repository.save(icon);
            writeMetadataSnapshot(icon);

            return update ? new FetchResult(0, 1, 0, 0) : new FetchResult(1, 0, 0, 0);
        } catch (IOException e) {
            if (config.debug()) {
                logger.log(Level.FINE, "Failed to fetch icon '" + meta.name()
                        + "': " + e.getMessage());
            }
            return new FetchResult(0, 0, 0, 1);
        }
    }

    /**
     * Ensures processed + thumbnail textures exist for an icon, regenerating
     * them from the cached original if missing or corrupt (recovery path).
     *
     * @param id icon id
     */
    public void ensureProcessed(UUID id) {
        try {
            boolean processedOk = images.readPngQuietly(cache.processedFile(id)) != null;
            boolean thumbOk = images.readPngQuietly(cache.thumbnailFile(id)) != null;
            if (processedOk && thumbOk) {
                return;
            }
            if (!cache.hasImage(id)) {
                return;
            }
            byte[] original = java.nio.file.Files.readAllBytes(cache.imageFile(id).toPath());
            BufferedImage decoded = images.decode(original);
            PluginConfig cfg = config.get();
            if (!processedOk) {
                images.writePng(images.toTexture(decoded, cfg), cache.processedFile(id));
            }
            if (!thumbOk) {
                images.writePng(images.toThumbnail(decoded, cfg), cache.thumbnailFile(id));
            }
        } catch (IOException e) {
            logger.log(Level.FINE, "Could not regenerate textures for " + id, e);
        }
    }

    private void writeMetadataSnapshot(Icon icon) {
        try {
            String json = "{\n"
                    + "  \"uuid\": \"" + icon.id() + "\",\n"
                    + "  \"title\": \"" + escape(icon.name()) + "\",\n"
                    + "  \"saint\": \"" + escape(icon.saint()) + "\",\n"
                    + "  \"feast\": \"" + escape(icon.feast()) + "\",\n"
                    + "  \"category\": \"" + escape(icon.category()) + "\",\n"
                    + "  \"tags\": \"" + escape(icon.tagsJoined()) + "\",\n"
                    + "  \"imageHash\": \"" + icon.imageHash() + "\",\n"
                    + "  \"provider\": \"" + escape(icon.providerId()) + "\",\n"
                    + "  \"license\": \"" + escape(icon.license().name()) + "\",\n"
                    + "  \"dateAdded\": " + icon.dateAdded().toEpochMilli() + ",\n"
                    + "  \"sourceUrl\": \"" + escape(icon.sourceUrl()) + "\"\n"
                    + "}\n";
            cache.writeAtomic(cache.metadataFile(icon.id()),
                    json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.log(Level.FINE, "Could not write metadata snapshot for " + icon.id(), e);
        }
    }

    private static String stableKey(IconMetadata meta) {
        return (meta.sourceUrl() == null || meta.sourceUrl().isBlank())
                ? meta.imageUrl() : meta.sourceUrl();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }

    /** Unused counter reserved for future per-provider stats. */
    @SuppressWarnings("unused")
    private static final AtomicInteger RESERVED = new AtomicInteger();
}
