// file: PaginatedMenu.java
// description: Abstract base for paginated menus. Adds navigation buttons (previous, close, next) in the bottom row.

package dev.zm.zonereset.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import dev.zm.zonereset.lang.LanguageManager;

import org.bukkit.Material;

public abstract class PaginatedMenu extends BaseMenu {

    protected int page = 1;
    protected int maxItemsPerPage = 45;
    protected int index = 0;
    private final LanguageManager langManager;

    public PaginatedMenu(Player player, LanguageManager langManager) {
        super(player);
        this.langManager = langManager;
    }

    @Override
    public int getSize() {
        return 54;
    }

    protected void addPaginationButtons() {
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, createItem(Material.BLACK_STAINED_GLASS_PANE, " "));
        }

        if (page > 1) {
            inventory.setItem(48, createItem(Material.ARROW,
                    langManager.getMessage("gui.previous-page"),
                    langManager.getMessageList("gui.previous-page-lore", "page", String.valueOf(page - 1))
                            .toArray(new String[0])));
        }

        inventory.setItem(49, createItem(Material.BARRIER, langManager.getMessage("gui.close")));
    }

    protected void handlePaginationClicks(InventoryClickEvent event, int totalItems) {
        int slot = event.getSlot();
        if (slot == 49) {
            player.closeInventory();
        } else if (slot == 48 && page > 1) {
            page--;
            open();
        } else if (slot == 50) {
            int nextStart = maxItemsPerPage * page;
            if (nextStart < totalItems) {
                page++;
                open();
            }
        }
    }
}