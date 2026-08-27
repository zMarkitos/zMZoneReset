package dev.zm.zonereset.scheduler;

import org.bukkit.World;

public interface ZoneScheduler {

    ZoneTask runSync(Runnable task);

    ZoneTask runSyncLater(Runnable task, long delayTicks);

    ZoneTask runSyncRepeating(Runnable task, long delayTicks, long periodTicks);

    ZoneTask runOnChunk(World world, int chunkX, int chunkZ, Runnable task);

    ZoneTask runOnChunkLater(World world, int chunkX, int chunkZ, Runnable task, long delayTicks);

    ZoneTask runAsync(Runnable task);

    ZoneTask runAsyncLater(Runnable task, long delayTicks);

    void shutdown();

    default ZoneTask runTaskTimer(ZoneTimerTask timerTask, long delayTicks, long periodTicks) {
        return runSyncRepeating(timerTask, delayTicks, periodTicks);
    }
}
