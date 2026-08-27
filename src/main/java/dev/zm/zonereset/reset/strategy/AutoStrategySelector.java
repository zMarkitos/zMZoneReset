package dev.zm.zonereset.reset.strategy;

import dev.zm.zonereset.api.reset.ResetStrategy;
import dev.zm.zonereset.api.zone.Zone;
import dev.zm.zonereset.diff.DiffTracker;

public final class AutoStrategySelector {

    private static final double MAX_DIFF_PERCENTAGE = 0.40;

    private final DiffTracker diffTracker;

    public AutoStrategySelector(DiffTracker diffTracker) {
        this.diffTracker = diffTracker;
    }

    public ResetStrategy selectStrategy(Zone zone) {
        if (zone.getResetStrategy() != ResetStrategy.AUTO) {
            return zone.getResetStrategy();
        }

        int changedBlocks = diffTracker.getChangeCount(zone.getId());

        if (changedBlocks == 0) {
            return ResetStrategy.DIFF;
        }

        long volume = zone.getBounds().getVolume();
        double changeRatio = (double) changedBlocks / volume;

        if (changeRatio > MAX_DIFF_PERCENTAGE) {
            return ResetStrategy.SNAPSHOT;
        }

        return ResetStrategy.DIFF;
    }
}
