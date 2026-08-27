// file: TimeBudget.java
// description: Represents a time/budget limit for heavy operations per tick.

package dev.zm.zonereset.core.performance;

public record TimeBudget(int blocksPerTick, long maxNanosPerTick) {

    public static final TimeBudget SAFE_DEFAULT = new TimeBudget(500, 2_000_000L);

    public static final int ABSOLUTE_MAX_BLOCKS = 5000;
    public static final long ABSOLUTE_MAX_NANOS = 10_000_000L;
}