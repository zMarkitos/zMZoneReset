// file: AdaptiveController.java
// description: Dynamically adjusts the time budget based on server MSPT to avoid lag.

package dev.zm.zonereset.core.performance;

public final class AdaptiveController {

    private final PerformanceMonitor monitor;

    private static final double MSPT_TARGET = 45.0;
    private static final int BASE_BLOCKS_PER_TICK = 1000;

    public AdaptiveController(PerformanceMonitor monitor) {
        this.monitor = monitor;
    }

    public TimeBudget calculateCurrentBudget() {
        double currentMSPT = monitor.getAverageMSPT();

        if (currentMSPT > MSPT_TARGET) {
            return new TimeBudget(100, 500_000L);
        }

        double headroom = 1.0 - (currentMSPT / MSPT_TARGET);
        if (headroom < 0)
            headroom = 0.0;

        int blocks = (int) (BASE_BLOCKS_PER_TICK * headroom);
        blocks = Math.max(100, Math.min(blocks, TimeBudget.ABSOLUTE_MAX_BLOCKS));

        return new TimeBudget(blocks, TimeBudget.ABSOLUTE_MAX_NANOS);
    }
}