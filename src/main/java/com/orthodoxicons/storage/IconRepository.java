package com.orthodoxicons.storage;

import com.orthodoxicons.model.Icon;
import com.orthodoxicons.model.License;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Repository for {@link Icon} persistence. All database access is funneled
 * through a single-threaded executor to serialize writes and guarantee thread
 * safety, while an in-memory {@link ConcurrentHashMap} cache serves reads to
 * the main thread without touching disk.
 */
public final class IconRepository {

    private final Database database;
    private final ExecutorService dbExecutor;
    private final Logger logger;
    private final ConcurrentHashMap<UUID, Icon> cache = new ConcurrentHashMap<>();

    /**
     * @param database   storage backend
     * @param dbExecutor single-threaded executor serializing DB access
     * @param logger     plugin logger
     */
    public IconRepository(Database database, ExecutorService dbExecutor, Logger logger) {
        this.database = database;
        this.dbExecutor = dbExecutor;
        this.logger = logger;
    }

    /**
     * Loads every icon from disk into the in-memory cache. Runs on the DB
     * executor.
     *
     * @return a future completed when the cache is warm
     */
    public CompletableFuture<Void> warmCache() {
        return CompletableFuture.runAsync(() -> {
            cache.clear();
            String sql = "SELECT * FROM icons";
            try (Connection c = database.connection();
                 PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Icon icon = map(rs);
                    cache.put(icon.id(), icon);
                }
                logger.info("Loaded " + cache.size() + " icons from storage.");
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to load icons", e);
            }
        }, dbExecutor);
    }

    /**
     * Inserts a new icon or updates an existing one (upsert), refreshing the
     * in-memory cache immediately.
     *
     * @param icon icon to persist
     * @return a future completed when the row is written
     */
    public CompletableFuture<Void> save(Icon icon) {
        cache.put(icon.id(), icon);
        return CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT INTO icons(id,name,saint,feast,category,description,source_url,
                    image_url,attribution,license,license_free,tags,provider,image_hash,
                    etag,last_modified,date_added)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET
                    name=excluded.name, saint=excluded.saint, feast=excluded.feast,
                    category=excluded.category, description=excluded.description,
                    source_url=excluded.source_url, image_url=excluded.image_url,
                    attribution=excluded.attribution, license=excluded.license,
                    license_free=excluded.license_free, tags=excluded.tags,
                    provider=excluded.provider, image_hash=excluded.image_hash,
                    etag=excluded.etag, last_modified=excluded.last_modified
                """;
            try (Connection c = database.connection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, icon.id().toString());
                ps.setString(2, icon.name());
                ps.setString(3, icon.saint());
                ps.setString(4, icon.feast());
                ps.setString(5, icon.category());
                ps.setString(6, icon.description());
                ps.setString(7, icon.sourceUrl());
                ps.setString(8, icon.imageUrl());
                ps.setString(9, icon.attribution());
                ps.setString(10, icon.license().name());
                ps.setInt(11, icon.license().isFree() ? 1 : 0);
                ps.setString(12, icon.tagsJoined());
                ps.setString(13, icon.providerId());
                ps.setString(14, icon.imageHash());
                ps.setString(15, icon.etag());
                ps.setString(16, icon.lastModified());
                ps.setLong(17, icon.dateAdded().toEpochMilli());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to save icon " + icon.id(), e);
            }
        }, dbExecutor);
    }

    /**
     * Deletes an icon by id.
     *
     * @param id icon id
     * @return a future completed when the row is removed
     */
    public CompletableFuture<Void> delete(UUID id) {
        cache.remove(id);
        return CompletableFuture.runAsync(() -> {
            try (Connection c = database.connection();
                 PreparedStatement ps = c.prepareStatement("DELETE FROM icons WHERE id=?")) {
                ps.setString(1, id.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to delete icon " + id, e);
            }
        }, dbExecutor);
    }

    /**
     * @param id icon id
     * @return the cached icon, if present
     */
    public Optional<Icon> find(UUID id) {
        return Optional.ofNullable(cache.get(id));
    }

    /**
     * @return an immutable snapshot of all cached icons
     */
    public List<Icon> all() {
        return new ArrayList<>(cache.values());
    }

    /**
     * @return the number of icons currently stored
     */
    public int count() {
        return cache.size();
    }

    /**
     * @return the set of distinct categories, sorted alphabetically
     */
    public List<String> categories() {
        return cache.values().stream()
                .map(Icon::category)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    /**
     * Case-insensitive substring search across name, saint, feast, category and
     * tags.
     *
     * @param query search text
     * @return matching icons
     */
    public List<Icon> search(String query) {
        String q = query == null ? "" : query.toLowerCase();
        if (q.isBlank()) {
            return all();
        }
        return cache.values().stream()
                .filter(i -> contains(i.name(), q) || contains(i.saint(), q)
                        || contains(i.feast(), q) || contains(i.category(), q)
                        || i.tags().stream().anyMatch(t -> contains(t, q)))
                .collect(Collectors.toList());
    }

    private static boolean contains(String haystack, String lowerNeedle) {
        return haystack != null && haystack.toLowerCase().contains(lowerNeedle);
    }

    private Icon map(ResultSet rs) throws SQLException {
        String tagsRaw = rs.getString("tags");
        List<String> tags = tagsRaw == null || tagsRaw.isBlank()
                ? List.of()
                : Arrays.stream(tagsRaw.split(",")).map(String::trim)
                        .filter(s -> !s.isEmpty()).collect(Collectors.toList());
        return Icon.builder()
                .id(UUID.fromString(rs.getString("id")))
                .name(rs.getString("name"))
                .saint(rs.getString("saint"))
                .feast(rs.getString("feast"))
                .category(rs.getString("category"))
                .description(rs.getString("description"))
                .sourceUrl(rs.getString("source_url"))
                .imageUrl(rs.getString("image_url"))
                .attribution(rs.getString("attribution"))
                .license(new License(rs.getString("license"), rs.getInt("license_free") == 1))
                .tags(tags)
                .providerId(rs.getString("provider"))
                .imageHash(rs.getString("image_hash"))
                .etag(rs.getString("etag"))
                .lastModified(rs.getString("last_modified"))
                .dateAdded(Instant.ofEpochMilli(rs.getLong("date_added")))
                .build();
    }
}
