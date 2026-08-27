// file: PerformanceMonitor.java
// description: Retrieves server MSPT (milliseconds per tick) using Paper API or fallback via TPS.

package dev.zm.zonereset.core.performance;

import org.bukkit.Bukkit;

import java.lang.reflect.Method;

public final class PerformanceMonitor {

    private final boolean hasPaperTickTimes;

    public PerformanceMonitor() {
        boolean paper = false;
        try {
            Method method = Bukkit.getServer().getClass().getMethod("getTickTimes");
            if (method.getReturnType() == long[].class) {
                paper = true;
            }
        } catch (Exception ignored) {
        }
        this.hasPaperTickTimes = paper;
    }

    public double getAverageMSPT() {
        if (hasPaperTickTimes) {
            try {
                long[] times = Bukkit.getServer().getTickTimes();
                if (times != null && times.length > 0) {
                    long sum = 0;
                    for (long t : times) {
                        sum += t;
                    }
                    double avgNanos = (double) sum / times.length;
                    return avgNanos / 1_000_000.0;
                }
            } catch (Exception ignored) {
            }
        }

        double tps = getTPS();
        if (tps >= 19.9) {
            return 30.0;
        } else if (tps > 0) {
            return 1000.0 / tps;
        }

        return 50.0;
    }

    public double getTPS() {
        try {
            double[] tps = Bukkit.getServer().getTPS();
            return tps[0];
        } catch (Exception e) {
            return 20.0;
        }
    }
}