package dev.zm.zonereset.api;

import dev.zm.zonereset.api.zone.Zone;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * API pública para gestionar zonas de zMZoneReset.
 *
 * <p>Obtener la instancia:
 * <pre>{@code
 * ZoneManagerAPI zoneManager = zMZoneReset.getInstance().getZoneManager();
 * }</pre>
 *
 * <p>Todas las operaciones de este manager que modifiquen estado deben llamarse
 * desde el hilo del servidor. Las lecturas son thread-safe.
 */
public interface ZoneManagerAPI {

    // Consultas

    /**
     * @param id identificador de la zona
     * @return la zona si existe, vacío si no
     */
    Optional<Zone> getZone(String id);

    /**
     * @return colección inmutable de todas las zonas registradas
     */
    Collection<Zone> getAllZones();

    /**
     * Devuelve las zonas que intersectan con el chunk especificado.
     *
     * <p>Operación O(1). Usar en listeners de eventos para evitar
     * iterar todas las zonas.
     *
     * @param worldUID UUID del mundo
     * @param chunkX   coordenada X del chunk
     * @param chunkZ   coordenada Z del chunk
     * @return set de zonas que afectan a ese chunk (puede estar vacío)
     */
    Set<Zone> getZonesAt(UUID worldUID, int chunkX, int chunkZ);

    /**
     * @param id ID de la zona
     * @return {@code true} si existe una zona con ese ID
     */
    boolean hasZone(String id);

    // Modificaciones (hilo del servidor)

    /**
     * Habilita una zona. La zona pasa al estado {@code READY} y comienza
     * a registrar cambios y programar resets automáticos.
     *
     * @param id ID de la zona
     * @throws IllegalArgumentException si la zona no existe
     * @throws IllegalStateException    si la zona ya está habilitada o en reset
     */
    void enableZone(String id);

    /**
     * Deshabilita una zona. La zona pasa al estado {@code DISABLED}.
     * No registra cambios ni realiza resets automáticos.
     *
     * @param id ID de la zona
     * @throws IllegalArgumentException si la zona no existe
     */
    void disableZone(String id);
}
