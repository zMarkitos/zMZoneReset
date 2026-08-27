package dev.zm.zonereset.reset.strategy;

import dev.zm.zonereset.core.logging.ZoneLogger;
import dev.zm.zonereset.reset.ResetJobImpl;
import dev.zm.zonereset.scheduler.ZoneScheduler;
import dev.zm.zonereset.snapshot.BlockEntitySerializer;
import dev.zm.zonereset.snapshot.BlockRecord;
import dev.zm.zonereset.snapshot.ChunkSnapshotData;
import dev.zm.zonereset.snapshot.ZoneSnapshot;
import dev.zm.zonereset.storage.SnapshotStorage;
import dev.zm.zonereset.util.BlockPosition;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class SnapshotStrategyHandler implements ResetStrategyHandler {

    private final SnapshotStorage storage;
    private final ZoneScheduler scheduler;
    private final BlockEntitySerializer entitySerializer;
    private final EntityCleaner entityCleaner;
    private final ZoneLogger logger;

    public SnapshotStrategyHandler(SnapshotStorage storage,
            ZoneScheduler scheduler,
            BlockEntitySerializer entitySerializer,
            EntityCleaner entityCleaner,
            ZoneLogger logger) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.entitySerializer = Objects.requireNonNull(entitySerializer, "entitySerializer");
        this.entityCleaner = Objects.requireNonNull(entityCleaner, "entityCleaner");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    private record SnapshotContext(ZoneSnapshot snapshot) {
    }

    @Override
    public CompletableFuture<Object> prepareAsync(ResetJobImpl job) {
        CompletableFuture<Object> future = new CompletableFuture<>();
        String zoneId = job.getZone().getId();

        scheduler.runAsync(() -> {
            try {
                Optional<ZoneSnapshot> optSnapshot = storage.loadSnapshot(zoneId);

                if (optSnapshot.isEmpty()) {
                    future.completeExceptionally(new IllegalStateException(
                            "No se encontró un snapshot para la zona '" + zoneId + "'."));
                    return;
                }

                ZoneSnapshot snapshot = optSnapshot.get();
                if (snapshot.getChunkCount() == 0 || snapshot.getTotalBlocks() == 0) {
                    future.completeExceptionally(
                            new RuntimeException("El snapshot de la zona '" + zoneId + "' está vacío (0 bloques)."));
                    return;
                }

                logger.info("SNAPSHOT prepare para '{}': {} chunks cargados desde disco.",
                        zoneId, snapshot.getChunkCount());

                future.complete(new SnapshotContext(snapshot));
            } catch (Exception e) {
                logger.error("Error cargando snapshot para zona '{}': {}", e, zoneId, e.getMessage());
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    @Override
    public CompletableFuture<Void> executeReset(ResetJobImpl job, World world, Object ctx) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        SnapshotContext context = (SnapshotContext) ctx;
        List<ChunkSnapshotData> chunks = context.snapshot().getChunks();

        if (chunks.isEmpty()) {
            future.completeExceptionally(new RuntimeException("Contexto de SNAPSHOT vacío"));
            return future;
        }

        int[] chunkIndex = { 0 };

        processNextChunk(job, world, chunks, chunkIndex, future);

        return future;
    }

    private void processNextChunk(ResetJobImpl job,
            World world,
            List<ChunkSnapshotData> chunks,
            int[] chunkIndex,
            CompletableFuture<Void> future) {

        int currentIndex = chunkIndex[0];
        ChunkSnapshotData chunkData = chunks.get(currentIndex);

        scheduler.runOnChunk(world, chunkData.getChunkX(), chunkData.getChunkZ(), () -> {
            try {
                if (!world.isChunkLoaded(chunkData.getChunkX(), chunkData.getChunkZ())) {
                    world.loadChunk(chunkData.getChunkX(), chunkData.getChunkZ(), true);
                }

                for (BlockRecord record : chunkData.getBlocks()) {
                    int x = BlockPosition.unpackX(record.packedPos);
                    int y = BlockPosition.unpackY(record.packedPos);
                    int z = BlockPosition.unpackZ(record.packedPos);

                    Block block = world.getBlockAt(x, y, z);

                    org.bukkit.block.data.BlockData bd = Bukkit.createBlockData(record.blockDataString);
                    block.setBlockData(bd, false);
                    block.getState().update(true, false);

                    if (record.hasBlockEntity()) {
                        entitySerializer.restoreBlockEntity(block, record.blockEntityNbt);
                    }
                }

                chunkIndex[0]++;
                double progress = (double) chunkIndex[0] / chunks.size();
                job.setProgress(progress);

                if (chunkIndex[0] >= chunks.size()) {
                    future.complete(null);
                } else {
                    scheduler.runSyncLater(() -> processNextChunk(job, world, chunks, chunkIndex, future), 1L);
                }

            } catch (Exception e) {
                logger.error("Error restoring chunk ({},{}) for zone '{}': {}",
                        e, chunkData.getChunkX(), chunkData.getChunkZ(), job.getZone().getId(), e.getMessage());
                future.completeExceptionally(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> verifyAndCleanup(ResetJobImpl job, World world, Object ctx) {
        return entityCleaner.cleanZone(world, job.getZone());
    }
}
