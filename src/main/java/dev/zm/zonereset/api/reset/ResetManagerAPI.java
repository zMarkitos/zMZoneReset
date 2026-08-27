package dev.zm.zonereset.api.reset;

import dev.zm.zonereset.api.zone.Zone;

import java.util.Collection;
import java.util.Optional;

/**
 * Gestor principal de los reseteos de zonas.
 *
 * <p>Controla la cola de reseteos, inicia nuevos trabajos,
 * y provee información sobre el estado actual del motor.
 */
public interface ResetManagerAPI {

    /**
     * Solicita un reset para la zona especificada.
     *
     * <p>La estrategia utilizada dependerá de la configuración de la zona
     * (AUTO, DIFF, o SNAPSHOT). Si es AUTO, el motor decidirá la mejor
     * opción en función del número de bloques modificados.
     *
     * @param zone la zona a resetear
     * @return el trabajo creado (pendiente o activo)
     * @throws IllegalStateException si la zona no está en un estado reseteable
     *                               (ej. ya está reseteándose o está deshabilitada)
     */
    ResetJob requestReset(Zone zone);

    /**
     * Solicita un reset forzando una estrategia específica, ignorando
     * la configuración de la zona.
     *
     * @param zone     la zona a resetear
     * @param strategy la estrategia a utilizar
     * @return el trabajo creado
     */
    ResetJob requestReset(Zone zone, ResetStrategy strategy);

    /**
     * @param zone la zona a consultar
     * @return el trabajo activo o en cola para la zona, si existe
     */
    Optional<ResetJob> getActiveJob(Zone zone);

    /**
     * @return colección inmutable de todos los trabajos en progreso y en cola
     */
    Collection<ResetJob> getAllJobs();

    /**
     * @return el trabajo que se está ejecutando actualmente, si lo hay
     */
    Optional<ResetJob> getCurrentActiveJob();
    
    /**
     * @return número de trabajos esperando en la cola
     */
    int getQueueSize();

    /**
     * Cancela todos los trabajos pendientes y detiene el motor de forma segura.
     * Los trabajos en ejecución terminarán su ciclo actual.
     */
    void shutdown();
}
