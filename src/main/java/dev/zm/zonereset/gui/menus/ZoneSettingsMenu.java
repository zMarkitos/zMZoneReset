package dev.zm.zonereset.gui.menus;

import dev.zm.zonereset.api.ZoneManagerAPI;
import dev.zm.zonereset.api.zone.Zone;
import dev.zm.zonereset.gui.BaseMenu;
import dev.zm.zonereset.lang.LanguageManager;
import dev.zm.zonereset.scheduler.ZoneScheduler;
import dev.zm.zonereset.zone.ZoneImpl;
import dev.zm.zonereset.zone.ZoneRepository;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ZoneSettingsMenu extends BaseMenu {

    public static final Map<UUID, String> pendingIntervalInput = new ConcurrentHashMap<>();

    private final Zone zone;
    private final ZoneManagerAPI zoneManager;
    private final ZoneRepository zoneRepository;
    private final LanguageManager langManager;
    private final ZoneScheduler scheduler;

    public ZoneSettingsMenu(Player player,
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
        return langManager.getMessage("gui.settings-title", "zone", zone.getId());
    }

    @Override
    public int getSize() {
        return 54;
    }

    @Override
    public void setMenuItems() {
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, createItem(Material.BLACK_STAINED_GLASS_PANE, " "));
        }

        String symbolTrue = langManager.getRawMessage("gui.symbol-true");
        if (symbolTrue == null)
            symbolTrue = "✓";
        String symbolFalse = langManager.getRawMessage("gui.symbol-false");
        if (symbolFalse == null)
            symbolFalse = "✗";

        String loreKey = zone.isEnabled() ? "gui.settings-toggle-lore-enabled" : "gui.settings-toggle-lore-disabled";
        List<String> toggleLore = langManager.getMessageList(loreKey);
        inventory.setItem(19, createItem(Material.LEVER,
                langManager.getMessage("gui.settings-toggle"),
                toggleLore.toArray(new String[0])));

        List<String> stratLore = langManager.getMessageList("gui.settings-strategy-lore",
                "strategy", zone.getResetStrategy().name());
        inventory.setItem(20, createItem(Material.COMMAND_BLOCK,
                langManager.getMessage("gui.settings-strategy"),
                stratLore.toArray(new String[0])));

        long intervalTicks = zone.getResetIntervalTicks();
        String intervalText = formatInterval(intervalTicks);
        List<String> intervalLore = langManager.getMessageList("gui.settings-interval-lore",
                "interval", intervalText);
        inventory.setItem(21, createItem(Material.CLOCK,
                langManager.getMessage("gui.settings-interval"),
                intervalLore.toArray(new String[0])));

        String blockKey = zone.isInteractionBlocked() ? "gui.settings-block-lore-true"
                : "gui.settings-block-lore-false";
        List<String> blockLore = langManager.getMessageList(blockKey);
        inventory.setItem(23, createItem(Material.IRON_DOOR,
                langManager.getMessage("gui.settings-block"),
                blockLore.toArray(new String[0])));

        String spawnSet = zone.getSpawn() != null ? symbolTrue : symbolFalse;
        List<String> spawnLore = langManager.getMessageList("gui.settings-spawn-lore", "status", spawnSet);
        inventory.setItem(24, createItem(Material.ENDER_PEARL,
                langManager.getMessage("gui.settings-spawn"),
                spawnLore.toArray(new String[0])));

        List<String> actionLore = langManager.getMessageList("gui.settings-action-lore",
                "action", zone.getPlayerResetAction().name());
        inventory.setItem(25, createItem(Material.COMPASS,
                langManager.getMessage("gui.settings-action"),
                actionLore.toArray(new String[0])));

        List<String> captureLore = langManager.getMessageList("gui.settings-capture-lore");
        inventory.setItem(30, createItem(Material.SCAFFOLDING,
                langManager.getMessage("gui.settings-capture"),
                captureLore.toArray(new String[0])));

        String titlesSet = zone.isShowTitles() ? symbolTrue : symbolFalse;
        List<String> titlesLore = langManager.getMessageList("gui.settings-titles-lore", "status", titlesSet);
        inventory.setItem(31, createItem(Material.PAPER,
                langManager.getMessage("gui.settings-titles"),
                titlesLore.toArray(new String[0])));

        String msgsSet = zone.isShowMessages() ? symbolTrue : symbolFalse;
        List<String> msgsLore = langManager.getMessageList("gui.settings-messages-lore", "status", msgsSet);
        inventory.setItem(32, createItem(Material.BOOK,
                langManager.getMessage("gui.settings-messages"),
                msgsLore.toArray(new String[0])));

        inventory.setItem(49, createItem(Material.ARROW,
                langManager.getMessage("gui.back")));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == 19) {
            if (zone.isEnabled()) {
                zoneManager.disableZone(zone.getId());
            } else {
                try {
                    zoneManager.enableZone(zone.getId());
                } catch (IllegalStateException ex) {
                    player.sendMessage("§c" + ex.getMessage());
                    return;
                }
            }
            persistAsync();
            open();

        } else if (slot == 20) {
            dev.zm.zonereset.api.reset.ResetStrategy s = zone.getResetStrategy();
            if (s == dev.zm.zonereset.api.reset.ResetStrategy.AUTO) {
                zone.setResetStrategy(dev.zm.zonereset.api.reset.ResetStrategy.DIFF);
            } else if (s == dev.zm.zonereset.api.reset.ResetStrategy.DIFF) {
                zone.setResetStrategy(dev.zm.zonereset.api.reset.ResetStrategy.SNAPSHOT);
            } else {
                zone.setResetStrategy(dev.zm.zonereset.api.reset.ResetStrategy.AUTO);
            }
            persistAsync();
            open();

        } else if (slot == 21) {
            pendingIntervalInput.put(player.getUniqueId(), zone.getId());
            player.closeInventory();
            player.sendMessage(langManager.getMessage("gui.settings-interval-chat-prompt"));

        } else if (slot == 23) {
            zone.setInteractionBlocked(!zone.isInteractionBlocked());
            persistAsync();
            open();

        } else if (slot == 24) {
            zone.setSpawn(player.getLocation());
            persistAsync();
            player.sendMessage(langManager.getMessage("prefix") + " "
                    + langManager.getMessage("zones.spawn-set", "zone", zone.getId()));
            open();

        } else if (slot == 25) {
            dev.zm.zonereset.zone.PlayerResetAction next = switch (zone.getPlayerResetAction()) {
                case ALLOW -> dev.zm.zonereset.zone.PlayerResetAction.WARN;
                case WARN -> dev.zm.zonereset.zone.PlayerResetAction.WARN_AND_BLOCK;
                case WARN_AND_BLOCK -> dev.zm.zonereset.zone.PlayerResetAction.TELEPORT;
                case TELEPORT -> dev.zm.zonereset.zone.PlayerResetAction.ALLOW;
            };
            zone.setPlayerResetAction(next);
            persistAsync();
            open();

        } else if (slot == 30) {
            player.closeInventory();
            Bukkit.dispatchCommand(player, "zmr capture " + zone.getId());

        } else if (slot == 31) {
            zone.setShowTitles(!zone.isShowTitles());
            persistAsync();
            open();

        } else if (slot == 32) {
            zone.setShowMessages(!zone.isShowMessages());
            persistAsync();
            open();

        } else if (slot == 49) {
            new ZoneMainMenu(player, zone, zoneManager, zoneRepository, langManager, scheduler).open();
        }
    }

    private void persistAsync() {
        if (!(zone instanceof ZoneImpl impl))
            return;
        scheduler.runAsync(() -> {
            try {
                zoneRepository.saveZone(impl);
            } catch (IOException e) {
            }
        });
    }

    private String formatInterval(long ticks) {
        if (ticks <= 0) {
            String disabledStr = langManager.getRawMessage("gui.interval-disabled");
            return disabledStr != null ? dev.zm.zonereset.lang.ColorParser.parse(disabledStr) : "§cDisabled";
        }
        long seconds = ticks / 20;
        long mins = seconds / 60;
        long secs = seconds % 60;
        if (mins > 0 && secs > 0)
            return mins + "m " + secs + "s";
        if (mins > 0)
            return mins + " min";
        return secs + "s";
    }
}
