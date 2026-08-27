package dev.zm.zonereset.api.reset;

import dev.zm.zonereset.api.zone.Zone;

import java.time.Instant;
import java.util.UUID;

/**
 * Representa un trabajo de reset en progreso o en cola para una zona.
 */
public interface ResetJob {

    /** @return identificador único de este trabajo */
    UUID getJobId();

    /** @return la zona asociada a este trabajo */
    Zone getZone();

    /** @return estado actual del trabajo */
    ResetJobState getState();

    /** @return momento en que el trabajo fue encolado */
    Instant getEnqueuedAt();

    /** @return progreso estimado de 0.0 a 1.0, o -1 si es indeterminado */
    double getProgress();

    /** @return la estrategia que está usando este trabajo */
    ResetStrategy getStrategy();
}
