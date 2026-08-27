package dev.zm.zonereset.scheduler.paper;

import dev.zm.zonereset.scheduler.ZoneScheduler;
import dev.zm.zonereset.scheduler.ZoneTask;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

public final class PaperScheduler implements ZoneScheduler {

    private final Plugin plugin;

    public PaperScheduler(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public ZoneTask runSync(Runnable task) {
        Objects.requireNonNull(task, "task");
        BukkitTask bt = plugin.getServer().getScheduler()
                .runTask(plugin, task);
        return wrap(bt);
    }

    @Override
    public ZoneTask runSyncLater(Runnable task, long delayTicks) {
        Objects.requireNonNull(task, "task");
        long delay = Math.max(0L, delayTicks);
        BukkitTask bt = plugin.getServer().getScheduler()
                .runTaskLater(plugin, task, delay);
        return wrap(bt);
    }

    @Override
    public ZoneTask runSyncRepeating(Runnable task, long delayTicks, long periodTicks) {
        Objects.requireNonNull(task, "task");
        long delay = Math.max(0L, delayTicks);
        long period = Math.max(1L, periodTicks);
        BukkitTask bt = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, task, delay, period);
        return wrap(bt);
    }

    @Override
    public ZoneTask runOnChunk(World world, int chunkX, int chunkZ, Runnable task) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(task, "task");
        return runSync(task);
    }

    @Override
    public ZoneTask runOnChunkLater(World world, int chunkX, int chunkZ,
            Runnable task, long delayTicks) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(task, "task");
        return runSyncLater(task, delayTicks);
    }

    @Override
    public ZoneTask runAsync(Runnable task) {
        Objects.requireNonNull(task, "task");
        BukkitTask bt = plugin.getServer().getScheduler()
                .runTaskAsynchronously(plugin, task);
        return wrap(bt);
    }

    @Override
    public ZoneTask runAsyncLater(Runnable task, long delayTicks) {
        Objects.requireNonNull(task, "task");
        long delay = Math.max(0L, delayTicks);
        BukkitTask bt = plugin.getServer().getScheduler()
                .runTaskLaterAsynchronously(plugin, task, delay);
        return wrap(bt);
    }

    @Override
    public void shutdown() {
        plugin.getServer().getScheduler().cancelTasks(plugin);
    }

    private static ZoneTask wrap(BukkitTask bt) {
        return new ZoneTask() {
            @Override
            public void cancel() {
                bt.cancel();
            }

            @Override
            public boolean isCancelled() {
                return bt.isCancelled();
            }
        };
    }
}
