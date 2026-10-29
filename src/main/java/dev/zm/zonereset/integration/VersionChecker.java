package dev.zm.zonereset.integration;

import dev.zm.zonereset.core.logging.ZoneLogger;
import dev.zm.zonereset.lang.LanguageManager;
import dev.zm.zonereset.scheduler.ZoneScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public final class VersionChecker {

    private final JavaPlugin plugin;
    private final ZoneScheduler scheduler;
    private final ZoneLogger logger;
    private final LanguageManager langManager;
    private static final int SPIGOT_RESOURCE_ID = 138318;

    private boolean updateAvailable = false;
    private String updateMessage = null;

    public VersionChecker(JavaPlugin plugin, ZoneScheduler scheduler, ZoneLogger logger, LanguageManager langManager) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.logger = logger;
        this.langManager = langManager;
    }

    public void checkAsync() {
        if (SPIGOT_RESOURCE_ID <= 0) {
            return;
        }
        scheduler.runAsync(() -> {
            try {
                URL url = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + SPIGOT_RESOURCE_ID);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String latest = reader.readLine();
                    String current = plugin.getDescription().getVersion();
                    if (latest != null && isNewerVersion(current, latest)) {
                        this.updateAvailable = true;
                        this.updateMessage = langManager.getMessage("general.update-available", "current", current,
                                "latest",
                                latest, "resource",
                                "https://www.spigotmc.org/resources/zmzonereset-automatic-zone-reset."
                                        + SPIGOT_RESOURCE_ID + "/");
                        Bukkit.getConsoleSender().sendMessage(updateMessage);

                        for (Player player : Bukkit.getOnlinePlayers()) {
                            if (player.hasPermission("zmzonereset.admin")) {
                                player.sendMessage(updateMessage);
                            }
                        }
                    } else {
                        Bukkit.getConsoleSender()
                                .sendMessage(langManager.getMessage("general.update-latest", "current", current));
                    }
                }
            } catch (Exception e) {
                logger.warn("Version check failed: {}", e.getMessage());
            }
        });
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getUpdateMessage() {
        return updateMessage;
    }

    private boolean isNewerVersion(String current, String latest) {
        try {
            String[] currParts = current.split("\\.");
            String[] latParts = latest.split("\\.");
            int length = Math.max(currParts.length, latParts.length);
            for (int i = 0; i < length; i++) {
                int c = i < currParts.length ? parsePart(currParts[i]) : 0;
                int l = i < latParts.length ? parsePart(latParts[i]) : 0;
                if (l > c)
                    return true;
                if (l < c)
                    return false;
            }
        } catch (Exception e) {
            return !current.equalsIgnoreCase(latest);
        }
        return false;
    }

    private int parsePart(String part) {
        String num = part.replaceAll("\\D", "");
        return num.isEmpty() ? 0 : Integer.parseInt(num);
    }
}
