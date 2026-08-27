package dev.zm.zonereset.snapshot;

import java.time.Instant;
import java.util.*;

public final class ZoneSnapshot {

    private final String zoneId;
    private final UUID worldUID;
    private final Instant capturedAt;
    private final List<ChunkSnapshotData> chunks;
    private final long totalBlocks;

    public ZoneSnapshot(String zoneId,
            UUID worldUID,
            Instant capturedAt,
            List<ChunkSnapshotData> chunks) {
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
        this.worldUID = Objects.requireNonNull(worldUID, "worldUID");
        this.capturedAt = Objects.requireNonNull(capturedAt, "capturedAt");
        this.chunks = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(chunks, "chunks")));
        this.totalBlocks = chunks.stream()
                .mapToLong(ChunkSnapshotData::getBlockCount)
                .sum();
    }

    public String getZoneId() {
        return zoneId;
    }

    public UUID getWorldUID() {
        return worldUID;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public List<ChunkSnapshotData> getChunks() {
        return chunks;
    }

    public int getChunkCount() {
        return chunks.size();
    }

    public long getTotalBlocks() {
        return totalBlocks;
    }

    public Optional<ChunkSnapshotData> getChunkData(int chunkX, int chunkZ) {
        for (ChunkSnapshotData chunk : chunks) {
            if (chunk.getChunkX() == chunkX && chunk.getChunkZ() == chunkZ) {
                return Optional.of(chunk);
            }
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return "ZoneSnapshot{zone='" + zoneId + "'" +
                ", chunks=" + chunks.size() +
                ", blocks=" + totalBlocks +
                ", capturedAt=" + capturedAt + "}";
    }
}
