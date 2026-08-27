package dev.zm.zonereset.integration;

import dev.zm.zonereset.api.ZoneManagerAPI;
import dev.zm.zonereset.config.ConfigManager;
import dev.zm.zonereset.core.logging.ZoneLogger;
import org.bukkit.Bukkit;

public final class IntegrationManager {

    private final ZoneLogger logger;
    private final ZoneManagerAPI zoneManager;
    private final ConfigManager configManager;
    private final String pluginVersion;

    public IntegrationManager(ZoneLogger logger, ZoneManagerAPI zoneManager, ConfigManager configManager, String pluginVersion) {
        this.logger = logger;
        this.zoneManager = zoneManager;
        this.configManager = configManager;
        this.pluginVersion = pluginVersion;
    }

    public void init() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            logger.info("PlaceholderAPI integration enabled.");
            new ZMRPlaceholderExpansion(zoneManager, configManager, pluginVersion).register();
        }
    }
}
