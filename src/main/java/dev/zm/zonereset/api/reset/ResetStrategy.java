package dev.zm.zonereset.api.reset;

/**
 * Estrategia de reset para una zona.
 *
 * <p>Define cómo el motor de reset determinará y aplicará los cambios
 * necesarios para restaurar la zona a su estado original.
 */
public enum ResetStrategy {

    /**
     * El plugin elige automáticamente entre {@link #DIFF} y {@link #SNAPSHOT}
     * según el porcentaje de bloques modificados respecto al volumen total de la zona.
     *
     * <p>Umbral configurable en:
     * <pre>
     * reset:
     *   auto:
     *     diff-threshold: 30   # % de bloques para cambiar a SNAPSHOT
     * </pre>
     *
     * <p>Lógica:
     * <pre>
     * si (bloques_cambiados / volumen_total) &lt; diff-threshold%  →  DIFF
     * si (bloques_cambiados / volumen_total) >= diff-threshold%  →  SNAPSHOT
     * </pre>
     */
    AUTO,

    /**
     * Solo restaura los bloques que fueron modificados desde el último reset
     * (o desde que se habilitó la zona).
     *
     * <p>Ventajas:
     * <ul>
     *   <li>Muy rápido cuando hay pocos cambios.</li>
     *   <li>Mínimo trabajo de I/O.</li>
     *   <li>Bajo consumo de RAM durante el reset.</li>
     * </ul>
     *
     * <p>Desventajas:
     * <ul>
     *   <li>Depende de que todos los eventos de cambio hayan sido capturados.</li>
     *   <li>Si se pierden eventos, puede quedar la zona en estado incorrecto.</li>
     * </ul>
     */
    DIFF,

    /**
     * Restaura completamente la zona desde el snapshot binario almacenado,
     * independientemente de cuántos bloques hayan cambiado.
     *
     * <p>Ventajas:
     * <ul>
     *   <li>Siempre correcto, no depende de la captura de eventos.</li>
     *   <li>Ideal para zonas con muchos cambios (Crystal PvP, Build &amp; Break).</li>
     * </ul>
     *
     * <p>Desventajas:
     * <ul>
     *   <li>Más lento que DIFF para zonas grandes con pocos cambios.</li>
     *   <li>Requiere que el snapshot esté capturado y sea válido.</li>
     * </ul>
     */
    SNAPSHOT
}
