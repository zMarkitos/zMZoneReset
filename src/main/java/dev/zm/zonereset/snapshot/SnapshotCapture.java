package dev.zm.zonereset.snapshot;

import dev.zm.zonereset.api.zone.Zone;
import dev.zm.zonereset.core.logging.ZoneLogger;
import dev.zm.zonereset.scheduler.ZoneScheduler;
import dev.zm.zonereset.storage.SnapshotStorage;
import dev.zm.zonereset.util.BlockPosition;
import dev.zm.zonereset.util.ChunkCoordinate;
import dev.zm.zonereset.zone.ZoneBounds;
import dev.zm.zonereset.zone.ZoneImpl;
import dev.zm.zonereset.zone.ZoneManagerImpl;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class SnapshotCapture {

    private final ZoneScheduler scheduler;
    private final SnapshotStorage storage;
    private final BlockEntitySerializer entitySerializer;
    private final ZoneManagerImpl zoneManager;
    private final ZoneLogger logger;

    public SnapshotCapture(ZoneScheduler scheduler,
            SnapshotStorage storage,
            BlockEntitySerializer entitySerializer,
            ZoneManagerImpl zoneManager,
            ZoneLogger logger) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.entitySerializer = Objects.requireNonNull(entitySerializer, "entitySerializer");
        this.zoneManager = Objects.requireNonNull(zoneManager, "zoneManager");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public CompletableFuture<Void> beginCapture(ZoneImpl zone,
            World world,
            Consumer<Double> onProgress) {
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(world, "world");

        CompletableFuture<Void> future = new CompletableFuture<>();
        ZoneBounds bounds = zone.getBounds();
        List<ChunkCoordinate> chunks = bounds.getChunks();

        logger.info("Starting snapshot capture for zone '{}'. {} chunks to process.",
                zone.getId(), chunks.size());

        List<ChunkSnapshotData> capturedChunks = new ArrayList<>(chunks.size());
        int[] progress = { 0 };

        processNextChunk(zone, world, bounds, chunks, capturedChunks,
                progress, future, onProgress);

        return future;
    }

    private void processNextChunk(ZoneImpl zone,
            World world,
            ZoneBounds bounds,
            List<ChunkCoordinate> chunks,
            List<ChunkSnapshotData> capturedChunks,
            int[] progress,
            CompletableFuture<Void> future,
            Consumer<Double> onProgress) {
        int idx = progress[0];
        if (idx >= chunks.size()) {
            finalizeCaptureAsync(zone, world, capturedChunks, future);
            return;
        }

        ChunkCoordinate chunkCoord = chunks.get(idx);

        scheduler.runOnChunk(world, chunkCoord.x(), chunkCoord.z(), () -> {
            try {
                if (!world.isChunkLoaded(chunkCoord.x(), chunkCoord.z())) {
                    world.loadChunk(chunkCoord.x(), chunkCoord.z(), true);
                }

                Chunk chunk = world.getChunkAt(chunkCoord.x(), chunkCoord.z());
                ChunkSnapshotData chunkData = captureChunk(chunk, bounds);
                capturedChunks.add(chunkData);

                progress[0]++;
                double progressRatio = (double) progress[0] / chunks.size();

                if (onProgress != null) {
                    onProgress.accept(progressRatio);
                }

                logger.debug("Captured chunk {}/{} ({},{}) — {} blocks.",
                        progress[0], chunks.size(),
                        chunkCoord.x(), chunkCoord.z(),
                        chunkData.getBlockCount());

                scheduler.runSyncLater(() -> processNextChunk(zone, world, bounds, chunks,
                        capturedChunks, progress, future, onProgress),
                        1L);

            } catch (Exception e) {
                logger.error("Error capturing chunk ({},{}) of zone '{}': {}",
                        e, chunkCoord.x(), chunkCoord.z(), zone.getId(), e.getMessage());
                future.completeExceptionally(
                        new RuntimeException("Error capturing chunk " +
                                chunkCoord + " of zone '" + zone.getId() + "'", e));
            }
        });
    }

    private ChunkSnapshotData captureChunk(Chunk chunk, ZoneBounds bounds) {
        List<BlockRecord> blocks = new ArrayList<>();

        int chunkMinX = chunk.getX() << 4;
        int chunkMinZ = chunk.getZ() << 4;

        int startX = Math.max(chunkMinX, bounds.getMinX());
        int endX = Math.min(chunkMinX + 15, bounds.getMaxX());
        int startZ = Math.max(chunkMinZ, bounds.getMinZ());
        int endZ = Math.min(chunkMinZ + 15, bounds.getMaxZ());
        int startY = bounds.getMinY();
        int endY = bounds.getMaxY();

        World world = chunk.getWorld();

        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                for (int y = startY; y <= endY; y++) {
                    Block block = world.getBlockAt(x, y, z);

                    long packedPos = BlockPosition.pack(x, y, z);
                    String blockDataStr = block.getBlockData().getAsString();

                    byte[] nbt = null;
                    if (block.getState() instanceof TileState) {
                        nbt = entitySerializer.captureBlockEntity(block);
                    }

                    blocks.add(new BlockRecord(packedPos, blockDataStr, nbt));
                }
            }
        }

        return new ChunkSnapshotData(chunk.getX(), chunk.getZ(), blocks);
    }

    private void finalizeCaptureAsync(ZoneImpl zone,
            World world,
            List<ChunkSnapshotData> capturedChunks,
            CompletableFuture<Void> future) {
        ZoneSnapshot snapshot = new ZoneSnapshot(
                zone.getId(),
                world.getUID(),
                Instant.now(),
                capturedChunks);

        logger.info("Capture of '{}' completed. Serializing {} blocks...",
                zone.getId(), snapshot.getTotalBlocks());

        if (snapshot.getTotalBlocks() == 0) {
            scheduler.runSync(() -> {
                zone.setSnapshotValid(false);
                future.completeExceptionally(new RuntimeException(
                        "Snapshot did not capture any blocks. Check the zone coordinates."));
            });
            return;
        }

        scheduler.runAsync(() -> {
            try {
                storage.saveSnapshot(zone.getId(), snapshot);
                scheduler.runSync(() -> {
                    zone.setSnapshotValid(true);
                    logger.info("Snapshot for zone '{}' saved and validated. " +
                            "{} chunks, {} total blocks.",
                            zone.getId(),
                            snapshot.getChunkCount(),
                            snapshot.getTotalBlocks());
                    future.complete(null);
                });

            } catch (Exception e) {
                logger.error("Error saving snapshot for zone '{}': {}",
                        e, zone.getId(), e.getMessage());
                scheduler.runSync(() -> {
                    zone.setSnapshotValid(false);
                    future.completeExceptionally(e);
                });
            }
        });
    }
}
