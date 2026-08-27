package dev.zm.zonereset.scheduler.folia;

import dev.zm.zonereset.scheduler.ZoneScheduler;
import dev.zm.zonereset.scheduler.ZoneTask;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FoliaScheduler implements ZoneScheduler {

    private static final Method GET_GLOBAL_REGION_SCHEDULER;
    private static final Method GET_REGION_SCHEDULER;
    private static final Method GET_ASYNC_SCHEDULER;

    private static final Method GLOBAL_RUN;
    private static final Method GLOBAL_RUN_DELAYED;
    private static final Method GLOBAL_RUN_AT_FIXED_RATE;
    private static final Method GLOBAL_CANCEL_TASKS;

    private static final Method REGION_RUN;
    private static final Method REGION_RUN_DELAYED;

    private static final Method ASYNC_RUN_NOW;
    private static final Method ASYNC_RUN_DELAYED;
    private static final Method ASYNC_CANCEL_TASKS;

    private static final Method SCHEDULED_TASK_CANCEL;
    private static final Method SCHEDULED_TASK_IS_CANCELLED;

    private static final Class<?> SCHEDULED_TASK_CLASS;

    static {
        try {
            Class<?> serverClass = Class.forName("org.bukkit.Server");
            Class<?> globalSchedulerClass = Class
                    .forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            Class<?> regionSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler");
            Class<?> asyncSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            SCHEDULED_TASK_CLASS = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");

            GET_GLOBAL_REGION_SCHEDULER = serverClass.getMethod("getGlobalRegionScheduler");
            GET_REGION_SCHEDULER = serverClass.getMethod("getRegionScheduler");
            GET_ASYNC_SCHEDULER = serverClass.getMethod("getAsyncScheduler");

            GLOBAL_RUN = globalSchedulerClass.getMethod("run",
                    org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class);
            GLOBAL_RUN_DELAYED = globalSchedulerClass.getMethod("runDelayed",
                    org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class, long.class);
            GLOBAL_RUN_AT_FIXED_RATE = globalSchedulerClass.getMethod("runAtFixedRate",
                    org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class,
                    long.class, long.class);
            GLOBAL_CANCEL_TASKS = globalSchedulerClass.getMethod("cancelTasks",
                    org.bukkit.plugin.Plugin.class);

            REGION_RUN = regionSchedulerClass.getMethod("run",
                    org.bukkit.plugin.Plugin.class, org.bukkit.World.class,
                    int.class, int.class, java.util.function.Consumer.class);
            REGION_RUN_DELAYED = regionSchedulerClass.getMethod("runDelayed",
                    org.bukkit.plugin.Plugin.class, org.bukkit.World.class,
                    int.class, int.class, java.util.function.Consumer.class, long.class);

            ASYNC_RUN_NOW = asyncSchedulerClass.getMethod("runNow",
                    org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class);
            ASYNC_RUN_DELAYED = asyncSchedulerClass.getMethod("runDelayed",
                    org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class,
                    long.class, TimeUnit.class);
            ASYNC_CANCEL_TASKS = asyncSchedulerClass.getMethod("cancelTasks",
                    org.bukkit.plugin.Plugin.class);

            SCHEDULED_TASK_CANCEL = SCHEDULED_TASK_CLASS.getMethod("cancel");
            SCHEDULED_TASK_IS_CANCELLED = SCHEDULED_TASK_CLASS.getMethod("isCancelled");

        } catch (Exception e) {
            throw new ExceptionInInitializerError(
                    "Error initializing FoliaScheduler via reflection. Are you running on a Folia server? Cause: "
                            + e.getMessage());
        }
    }

    private final Plugin plugin;
    private final Object globalScheduler;
    private final Object regionScheduler;
    private final Object asyncScheduler;

    public FoliaScheduler(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        try {
            Object server = plugin.getServer();
            this.globalScheduler = GET_GLOBAL_REGION_SCHEDULER.invoke(server);
            this.regionScheduler = GET_REGION_SCHEDULER.invoke(server);
            this.asyncScheduler = GET_ASYNC_SCHEDULER.invoke(server);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to obtain Folia schedulers.", e);
        }
    }

    @Override
    public ZoneTask runSync(Runnable task) {
        Objects.requireNonNull(task, "task");
        try {
            Object st = GLOBAL_RUN.invoke(globalScheduler, plugin,
                    (java.util.function.Consumer<?>) $ -> task.run());
            return wrapScheduledTask(st);
        } catch (Exception e) {
            throw new RuntimeException("FoliaScheduler.runSync failed", e);
        }
    }

    @Override
    public ZoneTask runSyncLater(Runnable task, long delayTicks) {
        Objects.requireNonNull(task, "task");
        long delay = Math.max(1L, delayTicks);
        try {
            Object st = GLOBAL_RUN_DELAYED.invoke(globalScheduler, plugin,
                    (java.util.function.Consumer<?>) $ -> task.run(), delay);
            return wrapScheduledTask(st);
        } catch (Exception e) {
            throw new RuntimeException("FoliaScheduler.runSyncLater failed", e);
        }
    }

    @Override
    public ZoneTask runSyncRepeating(Runnable task, long delayTicks, long periodTicks) {
        Objects.requireNonNull(task, "task");
        long delay = Math.max(1L, delayTicks);
        long period = Math.max(1L, periodTicks);
        try {
            Object st = GLOBAL_RUN_AT_FIXED_RATE.invoke(globalScheduler, plugin,
                    (java.util.function.Consumer<?>) $ -> task.run(), delay, period);
            return wrapScheduledTask(st);
        } catch (Exception e) {
            throw new RuntimeException("FoliaScheduler.runSyncRepeating failed", e);
        }
    }

    @Override
    public ZoneTask runOnChunk(World world, int chunkX, int chunkZ, Runnable task) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(task, "task");
        try {
            Object st = REGION_RUN.invoke(regionScheduler, plugin, world, chunkX, chunkZ,
                    (java.util.function.Consumer<?>) $ -> task.run());
            return wrapScheduledTask(st);
        } catch (Exception e) {
            throw new RuntimeException("FoliaScheduler.runOnChunk failed", e);
        }
    }

    @Override
    public ZoneTask runOnChunkLater(World world, int chunkX, int chunkZ,
            Runnable task, long delayTicks) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(task, "task");
        long delay = Math.max(1L, delayTicks);
        try {
            Object st = REGION_RUN_DELAYED.invoke(regionScheduler, plugin, world,
                    chunkX, chunkZ, (java.util.function.Consumer<?>) $ -> task.run(), delay);
            return wrapScheduledTask(st);
        } catch (Exception e) {
            throw new RuntimeException("FoliaScheduler.runOnChunkLater failed", e);
        }
    }

    @Override
    public ZoneTask runAsync(Runnable task) {
        Objects.requireNonNull(task, "task");
        try {
            Object st = ASYNC_RUN_NOW.invoke(asyncScheduler, plugin,
                    (java.util.function.Consumer<?>) $ -> task.run());
            return wrapScheduledTask(st);
        } catch (Exception e) {
            throw new RuntimeException("FoliaScheduler.runAsync failed", e);
        }
    }

    @Override
    public ZoneTask runAsyncLater(Runnable task, long delayTicks) {
        Objects.requireNonNull(task, "task");
        long delayMs = Math.max(0L, delayTicks) * 50L;
        try {
            Object st = ASYNC_RUN_DELAYED.invoke(asyncScheduler, plugin,
                    (java.util.function.Consumer<?>) $ -> task.run(),
                    delayMs, TimeUnit.MILLISECONDS);
            return wrapScheduledTask(st);
        } catch (Exception e) {
            throw new RuntimeException("FoliaScheduler.runAsyncLater failed", e);
        }
    }

    @Override
    public void shutdown() {
        try {
            GLOBAL_CANCEL_TASKS.invoke(globalScheduler, plugin);
            ASYNC_CANCEL_TASKS.invoke(asyncScheduler, plugin);
        } catch (Exception e) {
            plugin.getLogger().warning(
                    "[zMZoneReset] Error cancelling Folia tasks: " + e.getMessage());
        }
    }

    private static ZoneTask wrapScheduledTask(Object scheduledTask) {
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        return new ZoneTask() {
            @Override
            public void cancel() {
                if (cancelled.compareAndSet(false, true)) {
                    try {
                        SCHEDULED_TASK_CANCEL.invoke(scheduledTask);
                    } catch (Exception e) {
                    }
                }
            }

            @Override
            public boolean isCancelled() {
                if (cancelled.get())
                    return true;
                try {
                    Object result = SCHEDULED_TASK_IS_CANCELLED.invoke(scheduledTask);
                    return Boolean.TRUE.equals(result);
                } catch (Exception e) {
                    return cancelled.get();
                }
            }
        };
    }
}