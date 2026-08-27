package dev.zm.zonereset.api.reset;

/**
 * Estados posibles de un trabajo de reset (ResetJob).
 *
 * <p>El ciclo de vida normal es:
 * {@code PENDING} -> {@code PREPARING} -> {@code EXECUTING} -> {@code VERIFYING} -> {@code COMPLETED}
 *
 * <p>Si ocurre un error en cualquier etapa, pasa a {@code FAILED}.
 */
public enum ResetJobState {
    /** En cola, esperando turno. */
    PENDING,
    /** Preparando recursos (cargando chunks, leyendo snapshot). */
    PREPARING,
    /** Ejecutando cambios de bloques. */
    EXECUTING,
    /** Verificando entidades/drops post-reset. */
    VERIFYING,
    /** Reset finalizado con éxito. */
    COMPLETED,
    /** Reset fallido por un error inesperado o corrupción de datos. */
    FAILED
}
