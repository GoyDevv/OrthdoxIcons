package com.orthodoxicons.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents an icon that has been placed in the world as an item-frame map.
 * Persisted so that placements survive restarts and can be re-linked to their
 * spawned {@code ItemFrame} entity on chunk load.
 */
public final class PlacedIcon {

    private final UUID entityId;
    private final UUID iconId;
    private final UUID owner;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final String blockFace;
    private final int rotation;
    private final int mapId;

    /**
     * @param entityId the spawned item-frame entity UUID (primary key)
     * @param iconId   the icon displayed
     * @param owner    the placing player's UUID
     * @param world    world name
     * @param x        frame block X
     * @param y        frame block Y
     * @param z        frame block Z
     * @param blockFace the wall face the frame is attached to
     * @param rotation  frame rotation ordinal (0-7)
     * @param mapId     the Bukkit map id used for rendering
     */
    public PlacedIcon(UUID entityId, UUID iconId, UUID owner, String world,
                      int x, int y, int z, String blockFace, int rotation, int mapId) {
        this.entityId = Objects.requireNonNull(entityId, "entityId");
        this.iconId = Objects.requireNonNull(iconId, "iconId");
        this.owner = owner;
        this.world = Objects.requireNonNull(world, "world");
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockFace = Objects.requireNonNull(blockFace, "blockFace");
        this.rotation = rotation;
        this.mapId = mapId;
    }

    public UUID entityId() { return entityId; }
    public UUID iconId() { return iconId; }
    public UUID owner() { return owner; }
    public String world() { return world; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public String blockFace() { return blockFace; }
    public int rotation() { return rotation; }
    public int mapId() { return mapId; }
}
