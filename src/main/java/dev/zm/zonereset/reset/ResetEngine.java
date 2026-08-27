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

        currentJob = queue.poll().orElse(null);

        if (currentJob == null) {
            isProcessing.set(false);
            return;
        }

        logger.info("Iniciando ResetJob {} para zona '{}' (Estrategia: {})",
                currentJob.getJobId(),
                currentJob.getZone().getId(),
                currentJob.getStrategy());

        ZoneImpl zone = (ZoneImpl) currentJob.getZone();
        zone.setStatus(ZoneStatus.RESETTING);
        stateStore.markResetting(zone.getId());

        Bukkit.getPluginManager().callEvent(new ZoneResetStartEvent(currentJob));

        dev.zm.zonereset.api.reset.ResetStrategy strategy = currentJob.getStrategy();
        ResetStrategyHandler handler = strategy == dev.zm.zonereset.api.reset.ResetStrategy.DIFF
                ? diffHandler
                : snapshotHandler;

        World world = Bukkit.getWorld(zone.getWorldUID());
        if (world == null) {
            logger.error("El mundo de la zona '{}' no está cargado. Cancelando reset.", zone.getId());
            failJobAndContinue();
            return;
        }

        currentJob.setState(ResetJobState.PREPARING);
        handler.prepareAsync(currentJob)
                .thenComposeAsync(context -> {
                    currentJob.setState(ResetJobState.EXECUTING);
                    return handler.executeReset(currentJob, world, context)
                            .thenApply(v -> context);
                }, scheduler::runAsync)
                .thenComposeAsync(context -> {
                    currentJob.setState(ResetJobState.VERIFYING);
                    return handler.verifyAndCleanup(currentJob, world, context);
                }, scheduler::runAsync)
                .whenComplete((result, ex) -> {
                    scheduler.runSync(() -> {
                        if (ex != null) {
                            logger.error("Error durante ResetJob {} para zona '{}': {}", ex, currentJob.getJobId(),
                                    zone.getId(), ex.getMessage());
                            currentJob.markFailed();
                            Bukkit.getPluginManager().callEvent(new ZoneResetFailedEvent(currentJob, ex));
                        } else {
                            currentJob.setState(ResetJobState.COMPLETED);
                            currentJob.setProgress(1.0);
                            logger.info("ResetJob {} completado con éxito.", currentJob.getJobId());
                            Bukkit.getPluginManager().callEvent(new ZoneResetCompleteEvent(currentJob));
                        }

                        zone.setStatus(ZoneStatus.READY);
                        zone.resetTimer();
                        stateStore.clearResetting(zone.getId());
                        processNext();
                    });
                });
    }

    private void failJobAndContinue() {
        if (currentJob != null) {
            currentJob.markFailed();
            ZoneImpl failedZone = (ZoneImpl) currentJob.getZone();
            failedZone.setStatus(ZoneStatus.READY);
            failedZone.resetTimer();
            stateStore.clearResetting(currentJob.getZone().getId());
            Bukkit.getPluginManager()
                    .callEvent(new ZoneResetFailedEvent(currentJob, new RuntimeException("Job failed internally")));
        }
        processNext();
    }
}
