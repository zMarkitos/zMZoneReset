// file: MenuListener.java
// description: Listens to inventory events and routes them to the appropriate BaseMenu. Cancels all clicks to prevent item movement.

package dev.zm.zonereset.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public final class MenuListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof BaseMenu menu) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null)
                return;
            menu.handleClick(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof BaseMenu menu) {
            event.setCancelled(true);
            menu.handleDrag(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof BaseMenu menu) {
            menu.handleClose(event);
        }
    }
}