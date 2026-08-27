// file: ZoneMainMenu.java
// description: Main menu for a zone, providing buttons to settings, info, teleport, and back to list.

package dev.zm.zonereset.gui.menus;

import dev.zm.zonereset.api.ZoneManagerAPI;
import dev.zm.zonereset.api.zone.Zone;
import dev.zm.zonereset.gui.BaseMenu;
import dev.zm.zonereset.lang.LanguageManager;
import dev.zm.zonereset.scheduler.ZoneScheduler;
import dev.zm.zonereset.zone.ZoneRepository;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

public class ZoneMainMenu extends BaseMenu {

    private final Zone zone;
    private final ZoneManagerAPI zoneManager;
    private final ZoneRepository zoneRepository;
    private final LanguageManager langManager;
    private final ZoneScheduler scheduler;

    public ZoneMainMenu(Player player, Zone zone,
            ZoneManagerAPI zoneManager, ZoneRepository zoneRepository,
            LanguageManager langManager, ZoneScheduler scheduler) {
        super(player);
        this.zone = zone;
        this.zoneManager = zoneManager;
        this.zoneRepository = zoneRepository;
        this.langManager = langManager;
        this.scheduler = scheduler;
    }

    @Override
    public String getTitle() {
        return langManager.getMessage("gui.main-title", "zone", zone.getId());
    }

    @Override
    public int getSize() {
        return 27;
    }

    @Override
    public void setMenuItems() {
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, createItem(Material.BLACK_STAINED_GLASS_PANE, " "));
        }

        inventory.setItem(11, createItem(Material.COMPARATOR,
                langManager.getMessage("gui.main-settings"),
                langManager.getMessageList("gui.main-settings-lore").toArray(new String[0])));

        inventory.setItem(13, createItem(Material.BOOK,
                langManager.getMessage("gui.main-info"),
                langManager.getMessageList("gui.main-info-lore").toArray(new String[0])));

        inventory.setItem(15, createItem(Material.ENDER_PEARL,
                langManager.getMessage("gui.main-teleport"),
                langManager.getMessageList("gui.main-teleport-lore").toArray(new String[0])));

        inventory.setItem(22, createItem(Material.ARROW,
                langManager.getMessage("gui.back")));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot == 11) {
            new ZoneSettingsMenu(player, zone, zoneManager, zoneRepository, langManager, scheduler).open();
        } else if (slot == 13) {
            new ZoneInfoMenu(player, zone, zoneManager, zoneRepository, langManager, scheduler).open();
        } else if (slot == 15) {
            player.closeInventory();
            player.performCommand("zmr spawn " + zone.getId());
        } else if (slot == 22) {
            new ZonesListMenu(player, zoneManager, zoneRepository, langManager, scheduler).open();
        }
    }
}