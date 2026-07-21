package com.orthodoxicons.placement;

import com.orthodoxicons.item.IconItemKeys;
import com.orthodoxicons.model.PlacedIcon;
import com.orthodoxicons.storage.PlacedIconRepository;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

/**
 * Rebinds persisted map renderers to placed item frames when their chunks load,
 * ensuring icon artwork reappears after a server restart. Placement records
 * survive in the database; this listener reconnects them to live entities.
 */
public final class WorldListener implements Listener {

    private final PlacedIconRepository placedRepo;
    private final PlacementService placement;
    private final IconItemKeys keys;

    /**
     * @param placedRepo placed-icon repository
     * @param placement  placement service
     * @param keys       namespaced keys
     */
    public WorldListener(PlacedIconRepository placedRepo, PlacementService placement, IconItemKeys keys) {
        this.placedRepo = placedRepo;
        this.placement = placement;
        this.keys = keys;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (!(entity instanceof ItemFrame frame)) {
                continue;
            }
            if (!frame.getPersistentDataContainer().has(keys.marker(), PersistentDataType.INTEGER)) {
                continue;
            }
            Optional<PlacedIcon> placed = placedRepo.find(frame.getUniqueId());
            placed.ifPresent(placement::rebindMap);
        }
    }
}
