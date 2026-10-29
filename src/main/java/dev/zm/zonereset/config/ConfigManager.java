package dev.zm.zonereset.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigManager {

    private final JavaPlugin plugin;
    /**
     * Volatile so the timer (which may run on a different region thread on
     * Folia) always sees a fully-built configuration object.
     */
    private volatile PluginConfig pluginConfig;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        try (java.io.InputStream defConfigStream = plugin.getResource("config.yml")) {
            if (defConfigStream != null) {
                org.bukkit.configuration.file.YamlConfiguration defConfig = org.bukkit.configuration.file.YamlConfiguration
                        .loadConfiguration(
                                new java.io.InputStreamReader(defConfigStream,
                                        java.nio.charset.StandardCharsets.UTF_8));
                boolean changed = false;
                for (String key : defConfig.getKeys(true)) {
                    if (!config.contains(key)) {
                        config.set(key, defConfig.get(key));
                        changed = true;
                    }
                }
                if (changed) {
                    plugin.saveConfig();
                }
            }
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Could not read the bundled config.yml: " + e.getMessage());
        }

        // Build first, publish last. If parsing/config access fails, the
        // previous valid configuration remains active for every zone.
        PluginConfig loaded = new PluginConfig(config);
        this.pluginConfig = loaded;
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