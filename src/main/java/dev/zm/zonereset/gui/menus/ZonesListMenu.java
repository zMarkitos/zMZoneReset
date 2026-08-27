// file: ZonesListMenu.java
// description: Paginated list of all zones. Click on a zone to open its main menu.

package dev.zm.zonereset.gui.menus;

import dev.zm.zonereset.api.ZoneManagerAPI;
import dev.zm.zonereset.api.zone.Zone;
import dev.zm.zonereset.gui.PaginatedMenu;
import dev.zm.zonereset.lang.LanguageManager;
import dev.zm.zonereset.scheduler.ZoneScheduler;
import dev.zm.zonereset.zone.ZoneImpl;
import dev.zm.zonereset.zone.ZoneRepository;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ZonesListMenu extends PaginatedMenu {

    private final ZoneManagerAPI zoneManager;
    private final ZoneRepository zoneRepository;
    private final LanguageManager langManager;
    private final ZoneScheduler scheduler;
    private List<Zone> zones;

    public ZonesListMenu(Player player,
            ZoneManagerAPI zoneManager,
            ZoneRepository zoneRepository,
            LanguageManager langManager,
            ZoneScheduler scheduler) {
        super(player, langManager);
        this.zoneManager = zoneManager;
        this.zoneRepository = zoneRepository;
        this.langManager = langManager;
        this.scheduler = scheduler;
    }

    @Override
    public String getTitle() {
        return langManager.getMessage("gui.list-title", "page", String.valueOf(page));
    }

    @Override
    public void setMenuItems() {
        addPaginationButtons();

        zones = new ArrayList<>(zoneManager.getAllZones());

        if (zones.isEmpty())
            return;

        int startIndex = maxItemsPerPage * (page - 1);
        for (int i = 0; i < maxItemsPerPage; i++) {
            index = startIndex + i;
            if (index >= zones.size())
                break;

            Zone zone = zones.get(index);

            String snapshotStr = zone.hasValidSnapshot() ? "§a✓" : "§c✗";
            String name = langManager.getMessage("gui.zone-item-name", "zone", zone.getId());
            List<String> lore = langManager.getMessageList("gui.zone-item-lore",
                    "status", zone.getStatus().name(),
                    "strategy", zone.getResetStrategy().name(),
                    "snapshot", snapshotStr);

            inventory.setItem(i, createItem(Material.PAPER, name, lore.toArray(new String[0])));
        }

        if (index + 1 < zones.size()) {
            inventory.setItem(50, createItem(Material.ARROW, langManager.getMessage("gui.next-page")));
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot >= 0 && slot < maxItemsPerPage) {
            int zoneIndex = maxItemsPerPage * (page - 1) + slot;
            if (zoneIndex < zones.size()) {
                Zone zone = zones.get(zoneIndex);
                new ZoneMainMenu(player, zone, zoneManager, zoneRepository, langManager, scheduler).open();
            }
        } else {
            handlePaginationClicks(event, zones.size());
        }
    }
}