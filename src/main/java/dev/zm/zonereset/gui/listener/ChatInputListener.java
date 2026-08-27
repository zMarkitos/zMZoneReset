package dev.zm.zonereset.gui.listener;

import dev.zm.zonereset.api.ZoneManagerAPI;
import dev.zm.zonereset.api.zone.Zone;
import dev.zm.zonereset.gui.menus.ZoneSettingsMenu;
import dev.zm.zonereset.lang.LanguageManager;
import dev.zm.zonereset.scheduler.ZoneScheduler;
import dev.zm.zonereset.zone.ZoneImpl;
import dev.zm.zonereset.zone.ZoneRepository;
import dev.zm.zonereset.zone.ZoneValidator;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.io.IOException;

public class ChatInputListener implements Listener {

    private final ZoneManagerAPI zoneManager;
    private final ZoneRepository zoneRepository;
    private final LanguageManager langManager;
    private final ZoneScheduler scheduler;
    private final dev.zm.zonereset.core.logging.ZoneLogger logger;

    public ChatInputListener(ZoneManagerAPI zoneManager, ZoneRepository zoneRepository, LanguageManager langManager, ZoneScheduler scheduler, dev.zm.zonereset.core.logging.ZoneLogger logger) {
        this.zoneManager = zoneManager;
        this.zoneRepository = zoneRepository;
        this.langManager = langManager;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String zoneId = ZoneSettingsMenu.pendingIntervalInput.get(player.getUniqueId());

        if (zoneId != null) {
            event.setCancelled(true);
            String message = event.getMessage().trim();

            if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("cancelar")) {
                ZoneSettingsMenu.pendingIntervalInput.remove(player.getUniqueId());
                player.sendMessage(langManager.getMessage("gui.settings-interval-cancel"));
                
                scheduler.runSync(() -> {
                    zoneManager.getZone(zoneId).ifPresent(zone -> {
                        new ZoneSettingsMenu(player, zone, zoneManager, zoneRepository, langManager, scheduler).open();
                    });
                });
                return;
            }

            try {
                ZoneValidator validator = new ZoneValidator(logger);
                long ticks = validator.parseResetInterval(message);
                
                ZoneSettingsMenu.pendingIntervalInput.remove(player.getUniqueId());
                
                zoneManager.getZone(zoneId).ifPresent(zone -> {
                    zone.setResetIntervalTicks(ticks);
                    
                    if (zone instanceof ZoneImpl impl) {
                        try {
                            zoneRepository.saveZone(impl);
                        } catch (IOException e) {
                            logger.error("Error saving zone '{}' after interval update", e, zone.getId());
                        }
                    }
                    
                    player.sendMessage(langManager.getMessage("gui.settings-interval-success", "interval", message));
                    
                    scheduler.runSync(() -> {
                        new ZoneSettingsMenu(player, zone, zoneManager, zoneRepository, langManager, scheduler).open();
                    });
                });
                
            } catch (IllegalArgumentException e) {
                player.sendMessage(langManager.getMessage("gui.settings-interval-invalid"));
            }
        }
    }
}
