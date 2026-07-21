package com.orthodoxicons.cache;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Owns the on-disk cache directory layout and provides read/write helpers plus
 * cleanup routines.
 *
 * <pre>
 * plugins/OrthodoxIcons/cache/
 *   images/      original downloaded bytes ({uuid}.img)
 *   metadata/    per-icon metadata snapshots ({uuid}.json)
 *   processed/   map-ready textures ({uuid}.png)
 *   thumbnails/  small previews ({uuid}.png)
 * </pre>
 */
public final class CacheManager {

    private final Logger logger;
    private final File root;
    private final File images;
    private final File metadata;
    private final File processed;
    private final File thumbnails;

    /**
     * @param dataFolder the plugin data folder
     * @param logger     plugin logger
     */
    public CacheManager(File dataFolder, Logger logger) {
        this.logger = logger;
        this.root = new File(dataFolder, "cache");
        this.images = new File(root, "images");
        this.metadata = new File(root, "metadata");
        this.processed = new File(root, "processed");
        this.thumbnails = new File(root, "thumbnails");
    }

    /**
     * Creates the cache directory tree if missing.
     */
    public void initialize() {
        for (File dir : new File[]{root, images, metadata, processed, thumbnails}) {
            if (!dir.exists() && !dir.mkdirs()) {
                logger.warning("Could not create cache directory: " + dir);
            }
        }
    }

    public File imageFile(UUID id) { return new File(images, id + ".img"); }
    public File metadataFile(UUID id) { return new File(metadata, id + ".json"); }
    public File processedFile(UUID id) { return new File(processed, id + ".png"); }
    public File thumbnailFile(UUID id) { return new File(thumbnails, id + ".png"); }

    /**
     * @param id icon id
     * @return whether an original image is cached for the icon
     */
    public boolean hasImage(UUID id) {
        File f = imageFile(id);
        return f.exists() && f.length() > 0;
    }

    /**
     * Writes bytes to a cache file atomically (temp file + move).
     *
     * @param target destination file
     * @param data   bytes to write
     * @throws IOException on write failure
     */
    public void writeAtomic(File target, byte[] data) throws IOException {
        Path tmp = Files.createTempFile(target.getParentFile().toPath(), "tmp", ".part");
        Files.write(tmp, data);
        Files.move(tmp, target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Removes all cache artifacts for a single icon.
     *
     * @param id icon id
     */
    public void evict(UUID id) {
        deleteQuietly(imageFile(id));
        deleteQuietly(metadataFile(id));
        deleteQuietly(processedFile(id));
        deleteQuietly(thumbnailFile(id));
    }

    /**
     * Removes cache artifacts for icons whose ids are not in the live set.
     *
     * @param liveIds ids that still exist
     * @return number of files removed
     */
    public int cleanupOrphans(Set<UUID> liveIds) {
        int removed = 0;
        for (File dir : new File[]{images, metadata, processed, thumbnails}) {
            File[] files = dir.listFiles();
            if (files == null) {
                continue;
            }
            for (File file : files) {
                UUID id = parseId(file.getName());
                if (id != null && !liveIds.contains(id) && file.delete()) {
                    removed++;
                }
            }
        }
        return removed;
    }

    /**
     * Enforces a maximum total cache size by evicting the oldest originals
     * whose ids are not referenced by a placement.
     *
     * @param maxBytes    maximum cache size in bytes (0 = unlimited)
     * @param protectedIds ids that must never be evicted (e.g. placed icons)
     * @return number of files removed
     */
    public int enforceSizeLimit(long maxBytes, Set<UUID> protectedIds) {
        if (maxBytes <= 0) {
            return 0;
        }
        long total = totalSize();
        if (total <= maxBytes) {
            return 0;
        }
        File[] files = images.listFiles();
        if (files == null) {
            return 0;
        }
        int removed = 0;
        File[] sorted = files.clone();
        java.util.Arrays.sort(sorted, Comparator.comparingLong(File::lastModified));
        for (File file : sorted) {
            if (total <= maxBytes) {
                break;
            }
            UUID id = parseId(file.getName());
            if (id == null || protectedIds.contains(id)) {
                continue;
            }
            long size = file.length();
            evict(id);
            total -= size;
            removed++;
        }
        return removed;
    }

    /**
     * Clears the entire cache directory.
     *
     * @return number of files removed
     */
    public int clearAll() {
        int removed = 0;
        for (File dir : new File[]{images, metadata, processed, thumbnails}) {
            File[] files = dir.listFiles();
            if (files == null) {
                continue;
            }
            for (File file : files) {
                if (file.delete()) {
                    removed++;
                }
            }
        }
        return removed;
    }

    /**
     * @return total size of the cache in bytes
     */
    public long totalSize() {
        try (Stream<Path> stream = Files.walk(root.toPath())) {
            return stream.filter(Files::isRegularFile)
                    .mapToLong(p -> p.toFile().length())
                    .sum();
        } catch (IOException e) {
            logger.log(Level.FINE, "Failed to compute cache size", e);
            return 0;
        }
    }

    /**
     * @return the number of cached original images
     */
    public int imageCount() {
        File[] files = images.listFiles();
        return files == null ? 0 : files.length;
    }

    private static UUID parseId(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        try {
            return UUID.fromString(base);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void deleteQuietly(File file) {
        if (file.exists() && !file.delete()) {
            logger.log(Level.FINE, "Could not delete cache file: " + file);
        }
    }
}
