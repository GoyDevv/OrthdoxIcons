package com.orthodoxicons.placement;

import com.orthodoxicons.compat.CompatManager;
import com.orthodoxicons.config.ConfigManager;
import com.orthodoxicons.config.PluginConfig;
import com.orthodoxicons.item.IconItemFactory;
import com.orthodoxicons.item.IconItemKeys;
import com.orthodoxicons.model.Icon;
import com.orthodoxicons.model.PlacedIcon;
import com.orthodoxicons.storage.IconRepository;
import com.orthodoxicons.storage.PlacedIconRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Rotation;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.Optional;
import java.util.UUID;

/**
 * Service that places an icon on a wall as an item-frame-mounted map, and
 * removes placements again. Handles orientation, rotation, center alignment,
 * persistence, protection checks and per-player limits. All methods run on the
 * main thread (invoked from listeners).
 */
public final class PlacementService {

    /** Result of a placement attempt. */
    public enum Result { SUCCESS, NO_SPACE, PROTECTED, LIMIT_REACHED, DISABLED, ERROR }

    private final ConfigManager config;
    private final IconItemFactory itemFactory;
    private final IconItemKeys keys;
    private final IconRepository icons;
    private final PlacedIconRepository placedRepo;
    private final CompatManager compat;

    /**
     * @param config      configuration manager
     * @param itemFactory item factory
     * @param keys        namespaced keys
     * @param icons       icon repository
     * @param placedRepo  placed-icon repository
     * @param compat      compatibility/protection manager
     */
    public PlacementService(ConfigManager config, IconItemFactory itemFactory, IconItemKeys keys,
                            IconRepository icons, PlacedIconRepository placedRepo,
                            CompatManager compat) {
        this.config = config;
        this.itemFactory = itemFactory;
        this.keys = keys;
        this.icons = icons;
        this.placedRepo = placedRepo;
        this.compat = compat;
    }

    /**
     * Attempts to place the icon carried by {@code item} against the given wall
     * face of {@code clickedBlock}.
     *
     * @param player       the placing player
     * @param clickedBlock the block that was clicked
     * @param face         the face that was clicked (wall normal)
     * @param item         the icon item being placed
     * @return the placement result
     */
    public Result place(Player player, Block clickedBlock, BlockFace face, ItemStack item) {
        PluginConfig cfg = config.get();
        if (!cfg.placementEnabled()) {
            return Result.DISABLED;
        }
        Optional<UUID> iconId = itemFactory.readIconId(item);
        if (iconId.isEmpty()) {
            return Result.ERROR;
        }
        Optional<Icon> icon = icons.find(iconId.get());
        if (icon.isEmpty()) {
            return Result.ERROR;
        }
        // Only allow placement on vertical wall faces.
        if (face == BlockFace.UP || face == BlockFace.DOWN) {
            return Result.NO_SPACE;
        }
        Block target = clickedBlock.getRelative(face);
        if (!target.isEmpty() && !target.isPassable()) {
            return Result.NO_SPACE;
        }
        if (cfg.respectProtection() && !compat.protection().canBuild(player, target.getLocation())) {
            return Result.PROTECTED;
        }
        if (cfg.maxPerPlayer() > 0
                && placedRepo.countByOwner(player.getUniqueId()) >= cfg.maxPerPlayer()) {
            return Result.LIMIT_REACHED;
        }

        // Ensure no existing icon frame already occupies this exact face.
        if (frameExists(target, face)) {
            return Result.NO_SPACE;
        }

        int mapId = itemFactory.mapIdFor(icon.get());
        ItemStack mapItem = itemFactory.create(icon.get(), 1);

        Location spawnLoc = target.getLocation().add(0.5, 0.5, 0.5);
        ItemFrame frame;
        try {
            frame = target.getWorld().spawn(spawnLoc, ItemFrame.class, f -> {
                f.setFacingDirection(face, true);
                f.setItem(mapItem, false);
                f.setRotation(Rotation.NONE);
                f.setVisible(!cfg.invisibleFrame());
                f.setFixed(cfg.fixedFrame());
                f.setItemDropChance(0f);
                f.setPersistent(true);
                f.getPersistentDataContainer()
                        .set(keys.marker(), PersistentDataType.INTEGER, 1);
                f.getPersistentDataContainer()
                        .set(keys.iconId(), PersistentDataType.STRING, icon.get().id().toString());
            });
        } catch (IllegalArgumentException e) {
            // setFacingDirection throws if the frame cannot attach there.
            return Result.NO_SPACE;
        }

        if (frame == null || !frame.isValid()) {
            return Result.NO_SPACE;
        }

        PlacedIcon placed = new PlacedIcon(
                frame.getUniqueId(), icon.get().id(), player.getUniqueId(),
                target.getWorld().getName(), target.getX(), target.getY(), target.getZ(),
                face.name(), frame.getRotation().ordinal(), mapId);
        placedRepo.save(placed);
        return Result.SUCCESS;
    }

    /**
     * Removes a placed icon, dropping the original item when configured.
     *
     * @param frame  the item frame entity
     * @param placed the associated placement record
     * @param drop   whether to drop the original item
     */
    public void remove(ItemFrame frame, PlacedIcon placed, boolean drop) {
        Optional<Icon> icon = icons.find(placed.iconId());
        Location loc = frame.getLocation();
        frame.setItem(null, false);
        frame.remove();
        placedRepo.delete(placed.entityId());
        if (drop && config.get().dropOnBreak() && icon.isPresent() && loc.getWorld() != null) {
            loc.getWorld().dropItemNaturally(loc, itemFactory.create(icon.get(), 1));
        }
    }

    /**
     * Rebinds a persisted map view to its renderer after a restart, so the
     * texture is drawn again for the placed frame.
     *
     * @param placed the placement to rebind
     */
    public void rebindMap(PlacedIcon placed) {
        if (placed.mapId() < 0) {
            return;
        }
        MapView view = Bukkit.getMap(placed.mapId());
        if (view == null) {
            // Map data lost; regenerate a fresh view for the icon.
            icons.find(placed.iconId()).ifPresent(itemFactory::mapIdFor);
        }
    }

    private boolean frameExists(Block target, BlockFace face) {
        Location center = target.getLocation().add(0.5, 0.5, 0.5);
        for (var entity : target.getWorld().getNearbyEntities(center, 0.6, 0.6, 0.6)) {
            if (entity instanceof ItemFrame existing
                    && existing.getFacing() == face
                    && existing.getPersistentDataContainer()
                        .has(keys.marker(), PersistentDataType.INTEGER)) {
                Vector diff = existing.getLocation().toVector().subtract(center.toVector());
                if (diff.lengthSquared() < 0.25) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Consumes one icon item from a player's hand after a successful placement.
     *
     * @param player the player
     * @param hand   the hand used
     */
    public void consumeOne(Player player, EquipmentSlot hand) {
        ItemStack inHand = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        inHand.setAmount(inHand.getAmount() - 1);
    }
}
