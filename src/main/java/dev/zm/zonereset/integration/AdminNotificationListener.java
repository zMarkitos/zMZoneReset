package dev.zm.zonereset.integration;

import dev.zm.zonereset.lang.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class AdminNotificationListener implements Listener {

    private final LanguageManager langManager;

    public AdminNotificationListener(LanguageManager langManager) {
        this.langManager = langManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("zmzonereset.admin")) {
            return;
        }
        String message = langManager.getMessage("general.update-join");
        player.sendMessage(message);
        Bukkit.getConsoleSender().sendMessage(message);
    }
}
