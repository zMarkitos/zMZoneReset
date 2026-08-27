// file: BaseMenu.java
// description: Abstract base class for all GUIs. Implements InventoryHolder and provides utilities for item creation and color parsing.

package dev.zm.zonereset.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public abstract class BaseMenu implements InventoryHolder {

    protected Inventory inventory;
    protected final Player player;

    public BaseMenu(Player player) {
        this.player = player;
    }

    public abstract String getTitle();

    public abstract int getSize();

    public abstract void setMenuItems();

    public abstract void handleClick(InventoryClickEvent event);

    public void handleDrag(InventoryDragEvent event) {
    }

    public void handleClose(InventoryCloseEvent event) {
    }

    public void open() {
        inventory = Bukkit.createInventory(this, getSize(), getTitle());
        this.setMenuItems();
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    protected ItemStack createItem(org.bukkit.Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(dev.zm.zonereset.lang.ColorParser.parse(name));
            if (lore != null && lore.length > 0) {
                List<String> parsedLore = new java.util.ArrayList<>();
                for (String line : lore) {
                    parsedLore.add(dev.zm.zonereset.lang.ColorParser.parse(line));
                }
                meta.setLore(parsedLore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}