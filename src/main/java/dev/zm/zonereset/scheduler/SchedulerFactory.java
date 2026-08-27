package dev.zm.zonereset.scheduler;

import dev.zm.zonereset.scheduler.folia.FoliaScheduler;
import dev.zm.zonereset.scheduler.paper.PaperScheduler;
import org.bukkit.plugin.Plugin;

public final class SchedulerFactory {

    private static final String FOLIA_SENTINEL = "io.papermc.paper.threadedregions.RegionizedServer";

    private static volatile Boolean foliaDetected = null;

    private SchedulerFactory() {
    }

    public static ZoneScheduler create(Plugin plugin) {
        if (isFolia()) {
            return new FoliaScheduler(plugin);
        }
        return new PaperScheduler(plugin);
    }

    public static boolean isFolia() {
        if (foliaDetected == null) {
            synchronized (SchedulerFactory.class) {
                if (foliaDetected == null) {
                    foliaDetected = detectFolia();
                }
            }
        }
        return foliaDetected;
    }

    private static boolean detectFolia() {
        try {
            Class.forName(FOLIA_SENTINEL);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
