package dev.zm.zonereset.zone;

import dev.zm.zonereset.api.reset.ResetStrategy;
import dev.zm.zonereset.core.logging.ZoneLogger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class ZoneRepository {

    private static final String OLD_ZONES_DIR = "zones";
    private static final String ZONES_FILE = "zones.yml";

    private final File dataFolder;
    private final File zonesFile;
    private final ZoneLogger logger;
    private final ZoneValidator validator;

    public ZoneRepository(Plugin plugin, ZoneLogger logger, ZoneValidator validator) {
        Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.dataFolder = plugin.getDataFolder();
        this.zonesFile = new File(plugin.getDataFolder(), ZONES_FILE);
    }

    public void initialize() {
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            logger.error("No se pudo crear el directorio del plugin: {}", dataFolder.getAbsolutePath());
        }

        File oldZonesDir = new File(dataFolder, OLD_ZONES_DIR);
        if (oldZonesDir.exists() && oldZonesDir.isDirectory()) {
            logger.info("Detectado directorio antiguo 'zones/'. Iniciando migración a zones.yml...");
            migrateOldZones(oldZonesDir);
        }
    }

    private void migrateOldZones(File oldZonesDir) {
        File[] files = oldZonesDir.listFiles(f -> f.isFile() && f.getName().endsWith(".yml"));
        if (files == null || files.length == 0) {
            deleteDirectory(oldZonesDir);
            return;
        }

        YamlConfiguration newConfig = new YamlConfiguration();
        if (zonesFile.exists()) {
            newConfig = YamlConfiguration.loadConfiguration(zonesFile);
        }

        int migrated = 0;
        for (File file : files) {
            try {
                YamlConfiguration oldConfig = YamlConfiguration.loadConfiguration(file);
                String id = oldConfig.getString("id");
                if (id != null) {
                    newConfig.set("zones." + id, oldConfig);
                    migrated++;
                }
            } catch (Exception e) {
                logger.error("Error migrando archivo {}: {}", file.getName(), e.getMessage());
            }
        }

        try {
            newConfig.save(zonesFile);
            logger.info("Migración completada. {} zonas migradas a zones.yml.", migrated);
            deleteDirectory(oldZonesDir);
            logger.info("Directorio antiguo 'zones/' eliminado.");
        } catch (IOException e) {
            logger.error("Error guardando el nuevo zones.yml durante la migración: {}", e.getMessage());
        }
    }

    private void deleteDirectory(File dir) {
        File[] contents = dir.listFiles();
        if (contents != null) {
            for (File f : contents) {
                f.delete();
            }
        }
        dir.delete();
    }

    public List<ZoneImpl> loadAllZones() {
        List<ZoneImpl> loaded = new ArrayList<>();

        if (!zonesFile.exists()) {
            logger.debug("No hay archivo zones.yml.");
            return loaded;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(zonesFile);
        ConfigurationSection zonesSection = config.getConfigurationSection("zones");

        if (zonesSection == null) {
            return loaded;
        }

        for (String id : zonesSection.getKeys(false)) {
            ConfigurationSection zoneSection = zonesSection.getConfigurationSection(id);
            if (zoneSection == null)
                continue;

            try {
                ZoneImpl zone = loadZoneFromSection(id, zoneSection);
                if (zone != null) {
                    loaded.add(zone);
                    logger.debug("Zona '{}' cargada.", zone.getId());
                }
            } catch (Exception e) {
                logger.error("Error al cargar la zona '{}': {}", e, id, e.getMessage());
            }
        }

        logger.info("Cargadas {} zona(s).", loaded.size());
        return loaded;
    }

    private ZoneImpl loadZoneFromSection(String expectedId, ConfigurationSection config) {
        String id = config.getString("id", expectedId);
        String worldName = config.getString("world");

        if (worldName == null) {
            logger.error("Sección de zona '{}' incompleta (falta 'world'). Saltando.", id);
            return null;
        }

        List<String> idErrors = validator.validateId(id);
        if (!idErrors.isEmpty()) {
            logger.error("ID de zona inválido '{}': {}. Saltando.", id, String.join(", ", idErrors));
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            logger.warn("El mundo '{}' de la zona '{}' no está cargado. " +
                    "La zona se registrará pero permanecerá deshabilitada.", worldName, id);
        }

        ConfigurationSection min = config.getConfigurationSection("min");
        ConfigurationSection max = config.getConfigurationSection("max");

        if (min == null || max == null) {
            logger.error("Zona '{}': faltan secciones 'min' o 'max'. Saltando.", id);
            return null;
        }

        int minX = min.getInt("x", 0);
        int minY = min.getInt("y", 0);
        int minZ = min.getInt("z", 0);
        int maxX = max.getInt("x", 0);
        int maxY = max.getInt("y", 0);
        int maxZ = max.getInt("z", 0);

        ZoneBounds bounds = new ZoneBounds(minX, minY, minZ, maxX, maxY, maxZ);

        boolean enabled = config.getBoolean("enabled", true);
        boolean snapshotValid = config.getBoolean("snapshot-valid", false);
        boolean showTitles = config.getBoolean("show-titles", true);
        boolean showMessages = config.getBoolean("show-messages", true);

        String strategyStr = config.getString("reset.strategy", "AUTO");
        ResetStrategy strategy;
        try {
            strategy = ResetStrategy.valueOf(strategyStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Estrategia '{}' inválida en zona '{}'. Usando AUTO.", strategyStr, id);
            strategy = ResetStrategy.AUTO;
        }

        long resetIntervalTicks;
        String intervalStr = config.getString("reset.interval", "-1");
        try {
            resetIntervalTicks = validator.parseResetInterval(intervalStr);
        } catch (IllegalArgumentException e) {
            logger.warn("Intervalo de reset '{}' inválido en zona '{}'. Deshabilitando.", intervalStr, id);
            resetIntervalTicks = -1L;
        }

        String playerActionStr = config.getString("security.players.action-during-reset", "WARN_AND_BLOCK");
        PlayerResetAction playerAction;
        try {
            playerAction = PlayerResetAction.valueOf(playerActionStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Acción de jugador '{}' inválida en zona '{}'. Usando WARN_AND_BLOCK.", playerActionStr, id);
            playerAction = PlayerResetAction.WARN_AND_BLOCK;
        }

        UUID worldUID = (world != null) ? world.getUID() : parseUUIDSafe(config.getString("world-uid"), id);
        if (worldUID == null) {
            logger.error("No se puede determinar el UUID del mundo para la zona '{}'. Saltando.", id);
            return null;
        }

        return ZoneImpl.builder(id, worldUID, bounds)
                .resetStrategy(strategy)
                .resetIntervalTicks(resetIntervalTicks)
                .playerResetAction(playerAction)
                .enabled(enabled && world != null)
                .snapshotValid(snapshotValid)
                .showTitles(showTitles)
                .showMessages(showMessages)
                .build();
    }

    public void saveZone(ZoneImpl zone) throws IOException {
        Objects.requireNonNull(zone, "zone");

        YamlConfiguration config = new YamlConfiguration();
        if (zonesFile.exists()) {
            try {
                config.load(zonesFile);
            } catch (Exception e) {
                throw new IOException("Error leyendo zones.yml antes de guardar", e);
            }
        }

        String path = "zones." + zone.getId();

        config.set(path + ".id", zone.getId());
        config.set(path + ".world-uid", zone.getWorldUID().toString());

        World world = Bukkit.getWorld(zone.getWorldUID());
        if (world != null) {
            config.set(path + ".world", world.getName());
        }

        ZoneBounds bounds = zone.getBounds();
        config.set(path + ".min.x", bounds.getMinX());
        config.set(path + ".min.y", bounds.getMinY());
        config.set(path + ".min.z", bounds.getMinZ());
        config.set(path + ".max.x", bounds.getMaxX());
        config.set(path + ".max.y", bounds.getMaxY());
        config.set(path + ".max.z", bounds.getMaxZ());

        config.set(path + ".enabled", zone.isEnabled());
        config.set(path + ".snapshot-valid", zone.hasValidSnapshot());
        config.set(path + ".show-titles", zone.isShowTitles());
        config.set(path + ".show-messages", zone.isShowMessages());
        config.set(path + ".reset.strategy", zone.getResetStrategy().name());

        long ticks = zone.getResetIntervalTicks();
        config.set(path + ".reset.interval", ticks < 0 ? "-1" : String.valueOf(ticks));

        config.set(path + ".security.players.action-during-reset", zone.getPlayerResetAction().name());

        config.save(zonesFile);
        logger.debug("Zona '{}' guardada en zones.yml.", zone.getId());
    }

    public boolean deleteZone(String zoneId) {
        Objects.requireNonNull(zoneId, "zoneId");

        if (!zonesFile.exists())
            return false;

        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(zonesFile);
        } catch (Exception e) {
            logger.error("Error leyendo zones.yml al intentar borrar '{}': {}", zoneId, e.getMessage());
            return false;
        }

        if (config.contains("zones." + zoneId)) {
            config.set("zones." + zoneId, null);
            try {
                config.save(zonesFile);
                logger.debug("Zona '{}' eliminada de zones.yml.", zoneId);
                return true;
            } catch (IOException e) {
                logger.error("Error guardando zones.yml tras borrar '{}': {}", zoneId, e.getMessage());
                return false;
            }
        }

        return false;
    }

    public boolean zoneFileExists(String zoneId) {
        if (!zonesFile.exists())
            return false;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(zonesFile);
        return config.contains("zones." + zoneId);
    }

    private UUID parseUUIDSafe(String uuidStr, String zoneId) {
        if (uuidStr == null || uuidStr.isBlank())
            return null;
        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            logger.error("UUID de mundo inválido '{}' en zona '{}'.", uuidStr, zoneId);
            return null;
        }
    }
}
