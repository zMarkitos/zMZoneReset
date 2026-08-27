package dev.zm.zonereset.reset.recovery;

import dev.zm.zonereset.core.logging.ZoneLogger;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ResetStateStore {

    private final File recoveryFile;
    private final ZoneLogger logger;

    public ResetStateStore(File dataFolder, ZoneLogger logger) {
        this.recoveryFile = new File(dataFolder, "recovery.yml");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public synchronized void markResetting(String zoneId) {
        try {
            YamlConfiguration config = loadConfig();
            List<String> resetting = config.getStringList("resetting-zones");
            if (!resetting.contains(zoneId)) {
                resetting.add(zoneId);
                config.set("resetting-zones", resetting);
                config.save(recoveryFile);
            }
        } catch (IOException e) {
            logger.error("Error marcando zona {} para recovery", e, zoneId);
        }
    }

    public synchronized void clearResetting(String zoneId) {
        try {
            if (!recoveryFile.exists())
                return;

            YamlConfiguration config = loadConfig();
            List<String> resetting = config.getStringList("resetting-zones");
            if (resetting.remove(zoneId)) {
                config.set("resetting-zones", resetting);
                config.save(recoveryFile);
            }
        } catch (IOException e) {
            logger.error("Error limpiando zona {} de recovery", e, zoneId);
        }
    }

    public synchronized List<String> getPendingResets() {
        if (!recoveryFile.exists())
            return new ArrayList<>();
        return loadConfig().getStringList("resetting-zones");
    }

    private YamlConfiguration loadConfig() {
        if (!recoveryFile.exists()) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(recoveryFile);
    }
}
