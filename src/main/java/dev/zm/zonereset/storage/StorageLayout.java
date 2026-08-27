package dev.zm.zonereset.storage;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Objects;

public final class StorageLayout {

    public static final String ZONES_DIR = "zones";
    public static final String SNAPSHOTS_DIR = "snapshots";
    public static final String STATE_DIR = "state";

    public static final String SNAPSHOT_FILE = "snapshot.dat";
    public static final String SNAPSHOT_TEMP_FILE = "snapshot.tmp";
    public static final String SNAPSHOT_META_FILE = "snapshot.meta";

    public static final String ACTIVE_RESETS_FILE = "active_resets.json";
    public static final String STATS_FILE = "stats.json";

    private final File dataFolder;

    public StorageLayout(Plugin plugin) {
        this.dataFolder = Objects.requireNonNull(plugin, "plugin").getDataFolder();
    }

    public File getDataFolder() {
        return dataFolder;
    }

    public File getZonesDir() {
        return new File(dataFolder, ZONES_DIR);
    }

    public File getSnapshotsDir() {
        return new File(dataFolder, SNAPSHOTS_DIR);
    }

    public File getStateDir() {
        return new File(dataFolder, STATE_DIR);
    }

    public File getZoneSnapshotDir(String zoneId) {
        return new File(getSnapshotsDir(), zoneId);
    }

    public File getSnapshotFile(String zoneId) {
        return new File(getZoneSnapshotDir(zoneId), SNAPSHOT_FILE);
    }

    public File getSnapshotTempFile(String zoneId) {
        return new File(getZoneSnapshotDir(zoneId), SNAPSHOT_TEMP_FILE);
    }

    public File getSnapshotMetaFile(String zoneId) {
        return new File(getZoneSnapshotDir(zoneId), SNAPSHOT_META_FILE);
    }

    public File getActiveResetsFile() {
        return new File(getStateDir(), ACTIVE_RESETS_FILE);
    }

    public File getStatsFile() {
        return new File(getStateDir(), STATS_FILE);
    }

    public void ensureDirectories() {
        ensureDir(getZonesDir());
        ensureDir(getSnapshotsDir());
        ensureDir(getStateDir());
    }

    private void ensureDir(File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException(
                    "Could not create directory: " + dir.getAbsolutePath() +
                            ". Check file system permissions.");
        }
    }

    public void ensureZoneSnapshotDir(String zoneId) {
        ensureDir(getZoneSnapshotDir(zoneId));
    }
}