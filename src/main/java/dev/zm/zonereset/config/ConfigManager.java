package dev.zm.zonereset.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigManager {

    private final JavaPlugin plugin;
    private PluginConfig pluginConfig;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        this.pluginConfig = new PluginConfig(config);
    }

    public boolean isDebugMode() {
        return pluginConfig != null && pluginConfig.isDebugMode();
    }

    public int getMaxBlocksPerTick() {
        return pluginConfig != null ? pluginConfig.getMaxBlocksPerTick() : 1000;
    }

    public double getMaxMsptThreshold() {
        return pluginConfig != null ? pluginConfig.getMaxMsptThreshold() : 45.0;
    }

    public PluginConfig getConfig() {
        return pluginConfig;
    }
}