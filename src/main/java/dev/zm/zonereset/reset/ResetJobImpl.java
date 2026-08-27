package dev.zm.zonereset.reset;

import dev.zm.zonereset.api.reset.ResetJob;
import dev.zm.zonereset.api.reset.ResetJobState;
import dev.zm.zonereset.api.reset.ResetStrategy;
import dev.zm.zonereset.api.zone.Zone;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class ResetJobImpl implements ResetJob {

    private final UUID jobId;
    private final Zone zone;
    private final Instant enqueuedAt;
    private final ResetStrategy strategy;

    private final AtomicReference<ResetJobState> state;
    private volatile double progress;

    public ResetJobImpl(Zone zone, ResetStrategy strategy) {
        this.jobId = UUID.randomUUID();
        this.zone = Objects.requireNonNull(zone, "zone");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.enqueuedAt = Instant.now();
        this.state = new AtomicReference<>(ResetJobState.PENDING);
        this.progress = 0.0;
    }

    @Override
    public UUID getJobId() {
        return jobId;
    }

    @Override
    public Zone getZone() {
        return zone;
    }

    @Override
    public ResetJobState getState() {
        return state.get();
    }

    @Override
    public Instant getEnqueuedAt() {
        return enqueuedAt;
    }

    @Override
    public double getProgress() {
        return progress;
    }

    @Override
    public ResetStrategy getStrategy() {
        return strategy;
    }

    public boolean setState(ResetJobState newState) {
        ResetJobState current = state.get();
        if (current == ResetJobState.COMPLETED || current == ResetJobState.FAILED) {
            return false;
        }
        state.set(newState);
        return true;
    }

    public void markFailed() {
        state.set(ResetJobState.FAILED);
    }

    public void setProgress(double p) {
        this.progress = Math.max(0.0, Math.min(1.0, p));
    }
}
