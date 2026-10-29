package dev.zm.zonereset.reset;

import dev.zm.zonereset.api.reset.ResetJobState;
import dev.zm.zonereset.core.logging.ZoneLogger;
import dev.zm.zonereset.scheduler.ZoneScheduler;
import dev.zm.zonereset.zone.ZoneImpl;
import dev.zm.zonereset.zone.ZoneStatus;

import dev.zm.zonereset.reset.strategy.DiffStrategyHandler;
import dev.zm.zonereset.reset.strategy.ResetStrategyHandler;
import dev.zm.zonereset.reset.strategy.SnapshotStrategyHandler;
import dev.zm.zonereset.reset.recovery.ResetStateStore;

import dev.zm.zonereset.api.event.ZoneResetStartEvent;
import dev.zm.zonereset.api.event.ZoneResetCompleteEvent;
import dev.zm.zonereset.api.event.ZoneResetFailedEvent;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import org.bukkit.World;
import org.bukkit.Bukkit;

public final class ResetEngine {

    private final ResetQueue queue;
    private final ZoneScheduler scheduler;
    private final ZoneLogger logger;

    private final DiffStrategyHandler diffHandler;
    private final SnapshotStrategyHandler snapshotHandler;
    private final ResetStateStore stateStore;

    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private volatile ResetJobImpl currentJob = null;

    public ResetEngine(ResetQueue queue, ZoneScheduler scheduler, ZoneLogger logger,
            DiffStrategyHandler diffHandler,
            SnapshotStrategyHandler snapshotHandler,
            ResetStateStore stateStore) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.diffHandler = Objects.requireNonNull(diffHandler, "diffHandler");
        this.snapshotHandler = Objects.requireNonNull(snapshotHandler, "snapshotHandler");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
    }

    public void poke() {
        if (shutdown.get())
            return;

        if (isProcessing.compareAndSet(false, true)) {
            scheduler.runSync(this::processNext);
        }
    }

    public void shutdown() {
        shutdown.set(true);
        queue.clear();
        logger.info("ResetEngine stopped. Queue cleared.");
    }

    public ResetJobImpl getCurrentJob() {
        return currentJob;
    }

    private void processNext() {
        if (shutdown.get()) {
            isProcessing.set(false);
            return;
        }

        ResetJobImpl job = queue.poll().orElse(null);
        currentJob = job;

        if (job == null) {
            isProcessing.set(false);
            return;
        }

        logger.info("Iniciando ResetJob {} para zona '{}' (Estrategia: {})",
                job.getJobId(),
                job.getZone().getId(),
                job.getStrategy());

        ZoneImpl zone = (ZoneImpl) job.getZone();
        zone.setStatus(ZoneStatus.RESETTING);
        stateStore.markResetting(zone.getId());

        Bukkit.getPluginManager().callEvent(new ZoneResetStartEvent(job));

        dev.zm.zonereset.api.reset.ResetStrategy strategy = job.getStrategy();
        ResetStrategyHandler handler = strategy == dev.zm.zonereset.api.reset.ResetStrategy.DIFF
                ? diffHandler
                : snapshotHandler;

        World world = Bukkit.getWorld(zone.getWorldUID());
        if (world == null) {
            logger.error("El mundo de la zona '{}' no está cargado. Cancelando reset.", zone.getId());
            failJobAndContinue(job, new RuntimeException("World not loaded"));
            return;
        }

        try {
            job.setState(ResetJobState.PREPARING);
            handler.prepareAsync(job)
                    .thenComposeAsync(context -> {
                        job.setState(ResetJobState.EXECUTING);
                        return handler.executeReset(job, world, context)
                                .thenApply(v -> context);
                    }, scheduler::runAsync)
                    .thenComposeAsync(context -> {
                        job.setState(ResetJobState.VERIFYING);
                        return handler.verifyAndCleanup(job, world, context);
                    }, scheduler::runAsync)
                    .whenComplete((result, ex) -> {
                        // Completion is always marshalled back to the server thread.
                        scheduler.runSync(() -> {
                            if (ex != null) {
                                logger.error("Error durante ResetJob {} para zona '{}': {}", ex, job.getJobId(),
                                        zone.getId(), ex.getMessage());
                                job.markFailed();
                                Bukkit.getPluginManager().callEvent(new ZoneResetFailedEvent(job, ex));
                            } else {
                                job.setState(ResetJobState.COMPLETED);
                                job.setProgress(1.0);
                                logger.info("ResetJob {} completado con éxito.", job.getJobId());
                                Bukkit.getPluginManager().callEvent(new ZoneResetCompleteEvent(job));
                            }

                            zone.setStatus(ZoneStatus.READY);
                            zone.resetTimer();
                            stateStore.clearResetting(zone.getId());
                            currentJob = null;
                            processNext();
                        });
                    });
        } catch (Throwable t) {
            // A handler that throws synchronously must never leave the engine
            // permanently locked (which would block every future reset).
            logger.error("Error iniciando ResetJob {} para zona '{}': {}", t, job.getJobId(), zone.getId(),
                    t.getMessage());
            failJobAndContinue(job, t);
        }
    }

    private void failJobAndContinue(ResetJobImpl job, Throwable cause) {
        if (job != null) {
            job.markFailed();
            ZoneImpl failedZone = (ZoneImpl) job.getZone();
            failedZone.setStatus(ZoneStatus.READY);
            failedZone.resetTimer();
            stateStore.clearResetting(job.getZone().getId());
            Bukkit.getPluginManager()
                    .callEvent(new ZoneResetFailedEvent(job, cause));
        }
        currentJob = null;
        // Defer the next job instead of recursing: a burst of invalid jobs
        // (for example after a world was unloaded) can no longer overflow the
        // stack or monopolize the region thread.
        if (shutdown.get()) {
            isProcessing.set(false);
        } else {
            scheduler.runSync(this::processNext);
        }
    }
}
