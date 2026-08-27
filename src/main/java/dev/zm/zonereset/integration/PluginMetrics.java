package dev.zm.zonereset.integration;

import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

public final class PluginMetrics {

    public PluginMetrics(JavaPlugin plugin, int bStatsId) {
        new Metrics(plugin, bStatsId);
    }
}
