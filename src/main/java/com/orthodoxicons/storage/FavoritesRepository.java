package com.orthodoxicons.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository for per-player favorite icons. Backed by an in-memory index for
 * instant main-thread reads with writes serialized on the DB executor.
 */
public final class FavoritesRepository {

    private final Database database;
    private final ExecutorService dbExecutor;
    private final Logger logger;
    private final ConcurrentHashMap<UUID, Set<UUID>> byPlayer = new ConcurrentHashMap<>();

    /**
     * @param database   storage backend
     * @param dbExecutor single-threaded executor serializing DB access
     * @param logger     plugin logger
     */
    public FavoritesRepository(Database database, ExecutorService dbExecutor, Logger logger) {
        this.database = database;
        this.dbExecutor = dbExecutor;
        this.logger = logger;
    }

    /**
     * Loads all favorites into memory.
     *
     * @return a future completed when the index is warm
     */
    public CompletableFuture<Void> warmCache() {
        return CompletableFuture.runAsync(() -> {
            byPlayer.clear();
            try (Connection c = database.connection();
                 PreparedStatement ps = c.prepareStatement("SELECT player, icon_id FROM favorites");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID player = UUID.fromString(rs.getString("player"));
                    UUID icon = UUID.fromString(rs.getString("icon_id"));
                    byPlayer.computeIfAbsent(player, k -> ConcurrentHashMap.newKeySet()).add(icon);
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to load favorites", e);
            }
        }, dbExecutor);
    }

    /**
     * @param player player uuid
     * @param icon   icon uuid
     * @return whether the icon is favorited by the player
     */
    public boolean isFavorite(UUID player, UUID icon) {
        Set<UUID> set = byPlayer.get(player);
        return set != null && set.contains(icon);
    }

    /**
     * @param player player uuid
     * @return the player's favorite icon ids (possibly empty)
     */
    public Set<UUID> favorites(UUID player) {
        return byPlayer.getOrDefault(player, Set.of());
    }

    /**
     * Toggles a favorite and persists the change.
     *
     * @param player player uuid
     * @param icon   icon uuid
     * @return the new favorite state ({@code true} = now favorited)
     */
    public boolean toggle(UUID player, UUID icon) {
        Set<UUID> set = byPlayer.computeIfAbsent(player, k -> ConcurrentHashMap.newKeySet());
        boolean nowFavorite;
        if (set.contains(icon)) {
            set.remove(icon);
            nowFavorite = false;
        } else {
            set.add(icon);
            nowFavorite = true;
        }
        CompletableFuture.runAsync(() -> persist(player, icon, nowFavorite), dbExecutor);
        return nowFavorite;
    }

    private void persist(UUID player, UUID icon, boolean add) {
        String sql = add
                ? "INSERT OR IGNORE INTO favorites(player, icon_id) VALUES(?,?)"
                : "DELETE FROM favorites WHERE player=? AND icon_id=?";
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, icon.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to persist favorite", e);
        }
    }
}
