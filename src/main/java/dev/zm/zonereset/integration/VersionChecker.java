package dev.zm.zonereset.integration;

import dev.zm.zonereset.core.logging.ZoneLogger;
import dev.zm.zonereset.lang.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public final class VersionChecker {

    private final JavaPlugin plugin;
    private final ZoneLogger logger;
    private final LanguageManager langManager;
    private static final int SPIGOT_RESOURCE_ID = 138318;

    public VersionChecker(JavaPlugin plugin, ZoneLogger logger, LanguageManager langManager) {
        this.plugin = plugin;
        this.logger = logger;
        this.langManager = langManager;
    }

    public void checkAsync() {
        if (SPIGOT_RESOURCE_ID <= 0) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + SPIGOT_RESOURCE_ID);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String latest = reader.readLine();
                    String current = plugin.getDescription().getVersion();
                    if (latest != null && !latest.equalsIgnoreCase(current)) {
                        String msg = langManager.getMessage("general.update-available", "current", current, "latest",
                                latest, "resource", String.valueOf(SPIGOT_RESOURCE_ID));
                        logger.info("{}", msg);
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            if (player.hasPermission("zmzonereset.admin")) {
                                player.sendMessage(msg);
                            }
                        }
                    } else {
                        logger.info("{}", langManager.getMessage("general.update-latest", "current", current));
                    }
                }
            } catch (Exception e) {
                logger.warn("Version check failed: {}", e.getMessage());
            }
        });
    }
}
