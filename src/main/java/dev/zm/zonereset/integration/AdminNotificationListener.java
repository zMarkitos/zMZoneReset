package dev.zm.zonereset.integration;

import dev.zm.zonereset.lang.LanguageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class AdminNotificationListener implements Listener {

    private final LanguageManager langManager;
    private final VersionChecker versionChecker;

    public AdminNotificationListener(LanguageManager langManager, VersionChecker versionChecker) {
        this.langManager = langManager;
        this.versionChecker = versionChecker;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("zmzonereset.admin")) {
            return;
        }

        // Notificar sólo si hay una actualización disponible
        if (versionChecker.isUpdateAvailable()) {
            player.sendMessage(versionChecker.getUpdateMessage());
        }
    }
}
