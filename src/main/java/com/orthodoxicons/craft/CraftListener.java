package com.orthodoxicons.craft;

import com.orthodoxicons.gui.GuiManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Opens the icon browser when a player right-clicks while holding a crafted
 * blank-icon panel, tying the crafting flow to the GUI where the player chooses
 * which icon to consecrate.
 */
public final class CraftListener implements Listener {

    private final RecipeManager recipes;
    private final GuiManager gui;

    /**
     * @param recipes recipe manager
     * @param gui     gui manager
     */
    public CraftListener(RecipeManager recipes, GuiManager gui) {
        this.recipes = recipes;
        this.gui = gui;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!recipes.isBlankItem(item)) {
            return;
        }
        if (!player.hasPermission("orthodoxicons.use")) {
            return;
        }
        event.setCancelled(true);
        gui.open(player);
    }
}
