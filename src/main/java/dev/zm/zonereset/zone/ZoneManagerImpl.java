package dev.zm.zonereset.zone;

import dev.zm.zonereset.api.ZoneManagerAPI;
import dev.zm.zonereset.api.zone.Zone;
import dev.zm.zonereset.core.logging.ZoneLogger;
import dev.zm.zonereset.zone.index.ChunkZoneIndex;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementación interna del gestor de zonas.
 *
 * <p>
 * Responsabilidades:
 * <ul>
 * <li>Registro y desregistro de zonas en memoria.</li>
 * <li>Mantenimiento del {@link ChunkZoneIndex} actualizado.</li>
 * <li>Habilitación/deshabilitación de zonas.</li>
 * <li>Punto central de acceso a zonas para el resto del plugin.</li>
 * </ul>
 *
 * <p>
 * <b>Thread-safety</b>: el mapa interno usa {@link ConcurrentHashMap} para
 * lecturas seguras desde cualquier hilo. Las modificaciones (registro,
 * habilitación)
 * deben realizarse desde el hilo del servidor.
 */
public final class ZoneManagerImpl implements ZoneManagerAPI {

    private final ConcurrentHashMap<String, ZoneImpl> zones = new ConcurrentHashMap<>();
    private final ChunkZoneIndex chunkIndex;
    private final ZoneLogger logger;

    public ZoneManagerImpl(ChunkZoneIndex chunkIndex, ZoneLogger logger) {
        this.chunkIndex = Objects.requireNonNull(chunkIndex, "chunkIndex");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    // ZoneManagerAPI — Consultas

    @Override
    public Optional<Zone> getZone(String id) {
        if (id == null)
            return Optional.empty();
        return Optional.ofNullable(zones.get(id));
    }

    @Override
    public Collection<Zone> getAllZones() {
        return Collections.unmodifiableCollection(zones.values());
    }

    @Override
    public Set<Zone> getZonesAt(UUID worldUID, int chunkX, int chunkZ) {
        return chunkIndex.getZones(worldUID, chunkX, chunkZ);
    }

    @Override
    public boolean hasZone(String id) {
        return id != null && zones.containsKey(id);
    }

    // ZoneManagerAPI — Modificaciones

    @Override
    public void enableZone(String id) {
        ZoneImpl zone = requireZone(id);
        ZoneStatus current = zone.getStatus();
        if (current == ZoneStatus.RESETTING) {
            throw new IllegalStateException(
                    "La zona '" + id + "' está siendo reseteada y no puede habilitarse ahora.");
        }
        zone.setStatus(ZoneStatus.READY);
        logger.info("Zona '{}' habilitada.", id);
    }

    @Override
    public void disableZone(String id) {
        ZoneImpl zone = requireZone(id);
        zone.setStatus(ZoneStatus.DISABLED);
        logger.info("Zona '{}' deshabilitada.", id);
    }

    // Operaciones internas (para comandos y carga de config)

    /**
     * Registra una zona nueva en memoria y en el índice espacial.
     *
     * <p>
     * Si ya existe una zona con el mismo ID, lanza
     * {@link IllegalArgumentException}.
     *
     * @param zone zona a registrar
     * @throws IllegalArgumentException si el ID ya existe
     */
    public void registerZone(ZoneImpl zone) {
        Objects.requireNonNull(zone, "zone");
        String id = zone.getId();
        if (zones.putIfAbsent(id, zone) != null) {
            throw new IllegalArgumentException(
                    "Ya existe una zona con el ID '" + id + "'.");
        }
        chunkIndex.register(zone, zone.getWorldUID());
        logger.debug("Zona '{}' registrada. Chunks: {}.",
                id, zone.getBounds().getChunkCount());
    }

    public Optional<ZoneImpl> unregisterZone(String id) {
        ZoneImpl removed = zones.remove(id);
        if (removed != null) {
            chunkIndex.unregister(removed, removed.getWorldUID());
            logger.info("Zona '{}' eliminada.", id);
        }
        return Optional.ofNullable(removed);
    }

    public Optional<ZoneImpl> getZoneImpl(String id) {
        return Optional.ofNullable(zones.get(id));
    }

    public void shutdown() {
        chunkIndex.clear();
        zones.clear();
        logger.debug("ZoneManager apagado.");
    }

    private ZoneImpl requireZone(String id) {
        ZoneImpl zone = zones.get(id);
        if (zone == null) {
            throw new IllegalArgumentException("No existe ninguna zona con ID '" + id + "'.");
        }
        return zone;
    }

    public int getZoneCount() {
        return zones.size();
    }

    public void handleWorldUnload(UUID worldUID) {
        chunkIndex.clearWorld(worldUID);
        logger.debug("Limpiado índice espacial para el mundo {} (descargado)", worldUID);
    }
}
