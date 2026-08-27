// file: ZoneInfoMenu.java
// description: GUI showing detailed information about a specific zone (world, status, bounds, etc.).

package dev.zm.zonereset.gui.menus;

import dev.zm.zonereset.api.ZoneManagerAPI;
import dev.zm.zonereset.api.zone.Zone;
import dev.zm.zonereset.gui.BaseMenu;
import dev.zm.zonereset.lang.LanguageManager;
import dev.zm.zonereset.lang.TimeFormatter;
import dev.zm.zonereset.scheduler.ZoneScheduler;
import dev.zm.zonereset.zone.ZoneRepository;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

public class ZoneInfoMenu extends BaseMenu {

    private final Zone zone;
    private final ZoneManagerAPI zoneManager;
    private final ZoneRepository zoneRepository;
    private final LanguageManager langManager;
    private final ZoneScheduler scheduler;

    public ZoneInfoMenu(Player player,
            Zone zone,
            ZoneManagerAPI zoneManager,
            ZoneRepository zoneRepository,
            LanguageManager langManager,
            ZoneScheduler scheduler) {
        super(player);
        this.zone = zone;
        this.zoneManager = zoneManager;
        this.zoneRepository = zoneRepository;
        this.langManager = langManager;
        this.scheduler = scheduler;
    }

    @Override
    public String getTitle() {
        return langManager.getMessage("gui.info-title", "zone", zone.getId());
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

        World world = Bukkit.getWorld(zone.getWorldUID());
        String worldName = world != null ? world.getName() : zone.getWorldUID().toString();

        long intervalTicks = zone.getResetIntervalTicks();
        String intervalStr = intervalTicks <= 0 ? "N/A" : TimeFormatter.format(intervalTicks * 50L, 3);

        var bounds = zone.getBounds();

        List<String> infoLore = langManager.getMessageList("gui.info-general-lore",
                "zone", zone.getId(),
                "world", worldName,
                "status", zone.getStatus().name(),
                "strategy", zone.getResetStrategy().name(),
                "snapshot", zone.hasValidSnapshot() ? "§aYes" : "§cNo",
                "interval", intervalStr,
                "minx", String.valueOf(bounds.getMinX()),
                "miny", String.valueOf(bounds.getMinY()),
                "minz", String.valueOf(bounds.getMinZ()),
                "maxx", String.valueOf(bounds.getMaxX()),
                "maxy", String.valueOf(bounds.getMaxY()),
                "maxz", String.valueOf(bounds.getMaxZ()));

        inventory.setItem(13, createItem(Material.PAPER,
                langManager.getMessage("gui.info-general"),
                infoLore.toArray(new String[0])));

        inventory.setItem(22, createItem(Material.ARROW,
                langManager.getMessage("gui.back")));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot == 22) {
            new ZoneMainMenu(player, zone, zoneManager, zoneRepository, langManager, scheduler).open();
        }
    }
}