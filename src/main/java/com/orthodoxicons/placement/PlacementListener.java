package com.orthodoxicons.placement;

import com.orthodoxicons.config.ConfigManager;
import com.orthodoxicons.item.IconItemFactory;
import com.orthodoxicons.item.IconItemKeys;
import com.orthodoxicons.locale.LocaleManager;
import com.orthodoxicons.model.PlacedIcon;
import com.orthodoxicons.storage.PlacedIconRepository;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;
import java.util.UUID;

/**
 * Listens for icon placement (right-click a wall with an icon item) and removal
 * (breaking the frame), delegating to {@link PlacementService}. Also protects
 * placed frames from accidental rotation/removal when configured as fixed.
 */
public final class PlacementListener implements Listener {

    private final PlacementService placement;
    private final IconItemFactory itemFactory;
    private final IconItemKeys keys;
    private final PlacedIconRepository placedRepo;
    private final LocaleManager locale;
    private final ConfigManager config;

    /**
     * @param placement   placement service
     * @param itemFactory item factory
     * @param keys        namespaced keys
     * @param placedRepo  placed-icon repository
     * @param locale      locale manager
     * @param config      configuration manager
     */
    public PlacementListener(PlacementService placement, IconItemFactory itemFactory,
                             IconItemKeys keys, PlacedIconRepository placedRepo,
                             LocaleManager locale, ConfigManager config) {
        this.placement = placement;
        this.itemFactory = itemFactory;
        this.keys = keys;
        this.placedRepo = placedRepo;
        this.locale = locale;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (event.getHand() == null) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = event.getHand() == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (!itemFactory.isIconItem(item)) {
            return;
        }
        event.setCancelled(true);
        if (!player.hasPermission("orthodoxicons.place")) {
            locale.send(player, "general.no-permission");
            return;
        }
        Block clicked = event.getClickedBlock();
        BlockFace face = event.getBlockFace();
        PlacementService.Result result = placement.place(player, clicked, face, item);
        switch (result) {
            case SUCCESS -> {
                placement.consumeOne(player, event.getHand());
                locale.send(player, "placement.success");
            }
            case NO_SPACE -> locale.send(player, "placement.no-space");
            case PROTECTED -> locale.send(player, "placement.protected");
            case LIMIT_REACHED -> locale.send(player, "placement.limit-reached",
                    "limit", String.valueOf(config.get().maxPerPlayer()));
            case DISABLED -> locale.send(player, "placement.disabled");
            case ERROR -> { /* not an icon item after all - silent */ }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFrameDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ItemFrame frame) || !isTracked(frame)) {
            return;
        }
        if (!(event.getDamager() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        Optional<PlacedIcon> placed = placedRepo.find(frame.getUniqueId());
        if (placed.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        if (!player.hasPermission("orthodoxicons.place")) {
            locale.send(player, "general.no-permission");
            return;
        }
        placement.remove(frame, placed.get(), config.get().dropOnBreak());
        locale.send(player, "placement.removed");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractFrame(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame frame) || !isTracked(frame)) {
            return;
        }
        // Prevent players from swapping the map out of a placed frame.
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        if (!(event.getEntity() instanceof ItemFrame frame) || !isTracked(frame)) {
            return;
        }
        Optional<PlacedIcon> placed = placedRepo.find(frame.getUniqueId());
        if (placed.isEmpty()) {
            return;
        }
        if (event.getRemover() instanceof Player player) {
            event.setCancelled(true);
            placement.remove(frame, placed.get(), config.get().dropOnBreak());
            locale.send(player, "placement.removed");
        } else {
            // Non-player removal (explosions, etc.): keep the record consistent.
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (event.getEntity() instanceof ItemFrame frame && isTracked(frame)
                && event.getCause() != HangingBreakEvent.RemoveCause.ENTITY) {
            // Physics/obstruction: drop the icon and clean up the record.
            placedRepo.find(frame.getUniqueId()).ifPresent(placed ->
                    placement.remove(frame, placed, config.get().dropOnBreak()));
            event.setCancelled(true);
        }
    }

    private boolean isTracked(ItemFrame frame) {
        return frame.getPersistentDataContainer().has(keys.marker(), PersistentDataType.INTEGER);
    }

    /**
     * @param frame candidate
     * @return icon id stored on the frame, if any
     */
    public Optional<UUID> frameIcon(ItemFrame frame) {
        String raw = frame.getPersistentDataContainer().get(keys.iconId(), PersistentDataType.STRING);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
