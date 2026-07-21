package com.orthodoxicons.storage;

import com.orthodoxicons.model.PlacedIcon;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository for placed-icon persistence. Keeps an in-memory index of placed
 * icons keyed by item-frame entity id so the placement listener can look them
 * up on the main thread with no blocking I/O.
 */
public final class PlacedIconRepository {

    private final Database database;
    private final ExecutorService dbExecutor;
    private final Logger logger;
    private final ConcurrentHashMap<UUID, PlacedIcon> byEntity = new ConcurrentHashMap<>();

    /**
     * @param database   storage backend
     * @param dbExecutor single-threaded executor serializing DB access
     * @param logger     plugin logger
     */
    public PlacedIconRepository(Database database, ExecutorService dbExecutor, Logger logger) {
        this.database = database;
        this.dbExecutor = dbExecutor;
        this.logger = logger;
    }

    /**
     * Loads all placements into memory.
     *
     * @return a future completed when the index is warm
     */
    public CompletableFuture<Void> warmCache() {
        return CompletableFuture.runAsync(() -> {
            byEntity.clear();
            try (Connection c = database.connection();
                 PreparedStatement ps = c.prepareStatement("SELECT * FROM placed_icons");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PlacedIcon p = map(rs);
                    byEntity.put(p.entityId(), p);
                }
                logger.info("Loaded " + byEntity.size() + " placed icons.");
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to load placed icons", e);
            }
        }, dbExecutor);
    }

    /**
     * Persists a placement.
     *
     * @param placed the placement
     * @return a future completed when written
     */
    public CompletableFuture<Void> save(PlacedIcon placed) {
        byEntity.put(placed.entityId(), placed);
        return CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT INTO placed_icons(entity_id,icon_id,owner,world,x,y,z,block_face,rotation,map_id)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(entity_id) DO UPDATE SET
                    icon_id=excluded.icon_id, owner=excluded.owner, world=excluded.world,
                    x=excluded.x, y=excluded.y, z=excluded.z, block_face=excluded.block_face,
                    rotation=excluded.rotation, map_id=excluded.map_id
                """;
            try (Connection c = database.connection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, placed.entityId().toString());
                ps.setString(2, placed.iconId().toString());
                ps.setString(3, placed.owner() == null ? null : placed.owner().toString());
                ps.setString(4, placed.world());
                ps.setInt(5, placed.x());
                ps.setInt(6, placed.y());
                ps.setInt(7, placed.z());
                ps.setString(8, placed.blockFace());
                ps.setInt(9, placed.rotation());
                ps.setInt(10, placed.mapId());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to save placed icon " + placed.entityId(), e);
            }
        }, dbExecutor);
    }

    /**
     * Removes a placement by entity id.
     *
     * @param entityId item-frame entity id
     * @return a future completed when removed
     */
    public CompletableFuture<Void> delete(UUID entityId) {
        byEntity.remove(entityId);
        return CompletableFuture.runAsync(() -> {
            try (Connection c = database.connection();
                 PreparedStatement ps = c.prepareStatement("DELETE FROM placed_icons WHERE entity_id=?")) {
                ps.setString(1, entityId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to delete placed icon " + entityId, e);
            }
        }, dbExecutor);
    }

    /**
     * @param entityId item-frame entity id
     * @return the placement, if tracked
     */
    public Optional<PlacedIcon> find(UUID entityId) {
        return Optional.ofNullable(byEntity.get(entityId));
    }

    /**
     * @return a snapshot of all placements
     */
    public List<PlacedIcon> all() {
        return new ArrayList<>(byEntity.values());
    }

    /**
     * @return total number of placed icons
     */
    public int count() {
        return byEntity.size();
    }

    /**
     * Counts placements owned by a specific player.
     *
     * @param owner player uuid
     * @return the number of icons that player has placed
     */
    public long countByOwner(UUID owner) {
        return byEntity.values().stream()
                .filter(p -> owner.equals(p.owner()))
                .count();
    }

    private PlacedIcon map(ResultSet rs) throws SQLException {
        String ownerRaw = rs.getString("owner");
        return new PlacedIcon(
                UUID.fromString(rs.getString("entity_id")),
                UUID.fromString(rs.getString("icon_id")),
                ownerRaw == null ? null : UUID.fromString(ownerRaw),
                rs.getString("world"),
                rs.getInt("x"), rs.getInt("y"), rs.getInt("z"),
                rs.getString("block_face"),
                rs.getInt("rotation"),
                rs.getInt("map_id"));
    }
}
