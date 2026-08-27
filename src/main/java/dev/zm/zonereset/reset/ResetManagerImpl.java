package dev.zm.zonereset.reset;

import dev.zm.zonereset.api.reset.ResetJob;
import dev.zm.zonereset.api.reset.ResetManagerAPI;
import dev.zm.zonereset.api.reset.ResetStrategy;
import dev.zm.zonereset.api.zone.Zone;
import dev.zm.zonereset.core.logging.ZoneLogger;
import dev.zm.zonereset.zone.ZoneStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

import dev.zm.zonereset.reset.strategy.AutoStrategySelector;

public final class ResetManagerImpl implements ResetManagerAPI {

    private final ResetQueue queue;
    private final ResetEngine engine;
    private final ZoneLogger logger;
    private final AutoStrategySelector autoSelector;

    public ResetManagerImpl(ResetQueue queue, ResetEngine engine, ZoneLogger logger,
            AutoStrategySelector autoSelector) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.autoSelector = Objects.requireNonNull(autoSelector, "autoSelector");
    }

    @Override
    public ResetJob requestReset(Zone zone) {
        return requestReset(zone, zone.getResetStrategy());
    }

    @Override
    public ResetJob requestReset(Zone zone, ResetStrategy strategy) {
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(strategy, "strategy");

        if (zone.getStatus() == ZoneStatus.DISABLED) {
            throw new IllegalStateException(
                    "Cannot reset zone '" + zone.getId() + "' because it is disabled.");
        }

        if (zone.getStatus() == ZoneStatus.RESETTING) {
            throw new IllegalStateException("Zone '" + zone.getId() + "' is already being reset.");
        }

        if (queue.isQueued(zone.getId())) {
            throw new IllegalStateException("Zone '" + zone.getId() + "' already has a reset queued.");
        }

        ResetStrategy actualStrategy = resolveStrategy(zone, strategy);

        ResetJobImpl job = new ResetJobImpl(zone, actualStrategy);
        queue.enqueue(job);

        logger.info("Reset queued for zone '{}' (Strategy: {}, JobID: {})",
                zone.getId(), actualStrategy, job.getJobId());

        engine.poke();

        return job;
    }

    @Override
    public Optional<ResetJob> getActiveJob(Zone zone) {
        if (zone == null)
            return Optional.empty();

        ResetJob current = engine.getCurrentJob();
        if (current != null && current.getZone().getId().equals(zone.getId())) {
            return Optional.of(current);
        }

        return Optional.empty();
    }

    @Override
    public Collection<ResetJob> getAllJobs() {
        return new ArrayList<>();
    }

    @Override
    public Optional<ResetJob> getCurrentActiveJob() {
        return Optional.ofNullable(engine.getCurrentJob());
    }

    @Override
    public int getQueueSize() {
        return queue.size();
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down ResetManager...");
        engine.shutdown();
    }

    private ResetStrategy resolveStrategy(Zone zone, ResetStrategy requested) {
        if (requested != ResetStrategy.AUTO) {
            return requested;
        }

        return autoSelector.selectStrategy(zone);
    }
}
