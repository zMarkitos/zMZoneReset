package dev.zm.zonereset.reset.strategy;

import dev.zm.zonereset.api.reset.ResetStrategy;
import dev.zm.zonereset.api.zone.Zone;
import dev.zm.zonereset.config.ConfigManager;
import dev.zm.zonereset.diff.DiffTracker;

import java.util.Objects;

/**
 * Selecciona automáticamente entre DIFF y SNAPSHOT según el porcentaje
 * de bloques modificados vs el volumen total de la zona.
 *
 * <p>El umbral se lee de {@code reset.auto.diff-threshold} en config.yml
 * (por defecto 30%). Cuando el porcentaje de cambios supera el umbral,
 * se usa SNAPSHOT para garantizar una restauración completa y correcta.
 */
public final class AutoStrategySelector {

    private final DiffTracker diffTracker;
    private final ConfigManager configManager;

    public AutoStrategySelector(DiffTracker diffTracker, ConfigManager configManager) {
        this.diffTracker = Objects.requireNonNull(diffTracker, "diffTracker");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
    }

    public ResetStrategy selectStrategy(Zone zone) {
        if (zone.getResetStrategy() != ResetStrategy.AUTO) {
            return zone.getResetStrategy();
        }

        int changedBlocks = diffTracker.getChangeCount(zone.getId());

        // Sin cambios → DIFF (no hay nada que restaurar, es instantáneo)
        if (changedBlocks == 0) {
            return ResetStrategy.DIFF;
        }

        // Si no hay snapshot válido, forzar DIFF para evitar fallo de reset
        if (!zone.hasValidSnapshot()) {
            return ResetStrategy.DIFF;
        }

        long volume = zone.getBounds().getVolume();
        if (volume <= 0) {
            return ResetStrategy.DIFF;
        }

        // Umbral configurable desde config.yml (1-99 %)
        int thresholdPercent = (configManager.getConfig() != null)
                ? configManager.getConfig().getDiffThresholdPercent()
                : 30;
        double thresholdRatio = thresholdPercent / 100.0;

        double changeRatio = (double) changedBlocks / volume;

        return (changeRatio > thresholdRatio) ? ResetStrategy.SNAPSHOT : ResetStrategy.DIFF;
    }
}
