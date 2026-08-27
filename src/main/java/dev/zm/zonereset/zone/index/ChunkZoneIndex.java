package dev.zm.zonereset.zone.index;

import dev.zm.zonereset.api.zone.Zone;
import dev.zm.zonereset.util.ChunkCoordinate;
import dev.zm.zonereset.zone.ZoneBounds;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ChunkZoneIndex {

    private final ConcurrentHashMap<UUID, Map<Long, Set<Zone>>> worldIndex = new ConcurrentHashMap<>();

    public void register(Zone zone, UUID worldUID) {
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(worldUID, "worldUID");

        Map<Long, Set<Zone>> chunkMap = worldIndex.computeIfAbsent(
                worldUID, $ -> new ConcurrentHashMap<>());

        ZoneBounds bounds = zone.getBounds();
        List<ChunkCoordinate> chunks = bounds.getChunks();

        for (ChunkCoordinate chunk : chunks) {
            long packed = chunk.packed();
            chunkMap.computeIfAbsent(packed, $ -> new HashSet<>()).add(zone);
        }
    }

    public void unregister(Zone zone, UUID worldUID) {
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(worldUID, "worldUID");

        Map<Long, Set<Zone>> chunkMap = worldIndex.get(worldUID);
        if (chunkMap == null)
            return;

        ZoneBounds bounds = zone.getBounds();
        List<ChunkCoordinate> chunks = bounds.getChunks();

        for (ChunkCoordinate chunk : chunks) {
            long packed = chunk.packed();
            Set<Zone> zones = chunkMap.get(packed);
            if (zones != null) {
                zones.remove(zone);
                if (zones.isEmpty()) {
                    chunkMap.remove(packed, zones);
                }
            }
        }

        if (chunkMap.isEmpty()) {
            worldIndex.remove(worldUID, chunkMap);
        }
    }

    public void unregisterWorld(UUID worldUID) {
        worldIndex.remove(worldUID);
    }

    public Set<Zone> getZones(UUID worldUID, int chunkX, int chunkZ) {
        Map<Long, Set<Zone>> chunkMap = worldIndex.get(worldUID);
        if (chunkMap == null)
            return Collections.emptySet();

        long packed = ChunkCoordinate.pack(chunkX, chunkZ);
        Set<Zone> zones = chunkMap.get(packed);
        return zones == null ? Collections.emptySet() : Collections.unmodifiableSet(zones);
    }

    public Set<Zone> getZones(UUID worldUID, long packedChunk) {
        Map<Long, Set<Zone>> chunkMap = worldIndex.get(worldUID);
        if (chunkMap == null)
            return Collections.emptySet();

        Set<Zone> zones = chunkMap.get(packedChunk);
        return zones == null ? Collections.emptySet() : Collections.unmodifiableSet(zones);
    }

    public Set<Zone> getAllZonesInWorld(UUID worldUID) {
        Map<Long, Set<Zone>> chunkMap = worldIndex.get(worldUID);
        if (chunkMap == null)
            return Collections.emptySet();

        Set<Zone> result = new HashSet<>();
        for (Set<Zone> zones : chunkMap.values()) {
            result.addAll(zones);
        }
        return Collections.unmodifiableSet(result);
    }

    public int getWorldCount() {
        return worldIndex.size();
    }

    public int getTotalChunkEntries() {
        return worldIndex.values().stream()
                .mapToInt(Map::size)
                .sum();
    }

    public void clear() {
        worldIndex.clear();
    }

    public void clearWorld(UUID worldUID) {
        if (worldUID != null) {
            worldIndex.remove(worldUID);
        }
    }
}
