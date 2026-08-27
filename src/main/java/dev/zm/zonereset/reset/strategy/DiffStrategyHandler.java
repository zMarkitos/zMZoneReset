package dev.zm.zonereset.reset.strategy;

import dev.zm.zonereset.core.logging.ZoneLogger;
import dev.zm.zonereset.diff.BlockChange;
import dev.zm.zonereset.diff.DiffStore;
import dev.zm.zonereset.reset.ResetJobImpl;
import dev.zm.zonereset.scheduler.ZoneScheduler;
import dev.zm.zonereset.snapshot.BlockEntitySerializer;
import dev.zm.zonereset.snapshot.BlockRecord;
import dev.zm.zonereset.util.BlockPosition;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class DiffStrategyHandler implements ResetStrategyHandler {

    private static final int BLOCKS_PER_TICK = 500;

    private final DiffStore diffStore;
    private final ZoneScheduler scheduler;
    private final BlockEntitySerializer entitySerializer;
    private final EntityCleaner entityCleaner;
    private final ZoneLogger logger;

    public DiffStrategyHandler(DiffStore diffStore,
            ZoneScheduler scheduler,
            BlockEntitySerializer entitySerializer,
            EntityCleaner entityCleaner,
            ZoneLogger logger) {
        this.diffStore = Objects.requireNonNull(diffStore, "diffStore");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.entitySerializer = Objects.requireNonNull(entitySerializer, "entitySerializer");
        this.entityCleaner = Objects.requireNonNull(entityCleaner, "entityCleaner");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    private record ChunkDiffData(int chunkX, int chunkZ, List<BlockChange> changes) {}

    private record DiffContext(List<ChunkDiffData> chunks) {}

    @Override
    public CompletableFuture<Object> prepareAsync(ResetJobImpl job) {
        CompletableFuture<Object> future = new CompletableFuture<>();
        String zoneId = job.getZone().getId();

        Collection<BlockChange> rawChanges = diffStore.getChanges(zoneId);

        java.util.Map<Long, List<BlockChange>> chunkGroups = new java.util.HashMap<>();
        for (BlockChange change : rawChanges) {
            int x = BlockPosition.unpackX(change.record().packedPos);
            int z = BlockPosition.unpackZ(change.record().packedPos);
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            long chunkKey = ((long) chunkX & 0xFFFFFFFFL) | (((long) chunkZ) << 32);
            chunkGroups.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(change);
        }

        List<ChunkDiffData> chunks = new ArrayList<>();
        for (java.util.Map.Entry<Long, List<BlockChange>> entry : chunkGroups.entrySet()) {
            long key = entry.getKey();
            int chunkX = (int) (key & 0xFFFFFFFFL);
            int chunkZ = (int) (key >> 32);
            chunks.add(new ChunkDiffData(chunkX, chunkZ, entry.getValue()));
        }

        logger.info("DIFF prepare para '{}': {} bloques a restaurar en {} chunks.", zoneId, rawChanges.size(), chunks.size());

        diffStore.clearZone(zoneId);

        if (chunks.isEmpty()) {
            logger.debug("No hay bloques que restaurar en la zona '{}'. Omitiendo reseteo de bloques.", zoneId);
            future.complete(new DiffContext(chunks));
            return future;
        }

        future.complete(new DiffContext(chunks));
        return future;
    }

    @Override
    public CompletableFuture<Void> executeReset(ResetJobImpl job, World world, Object ctx) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        DiffContext context = (DiffContext) ctx;
        List<ChunkDiffData> chunks = context.chunks();

        if (chunks.isEmpty()) {
            future.complete(null);
            return future;
        }

        int[] chunkIndex = { 0 };

        processNextChunk(job, world, chunks, chunkIndex, future);

        return future;
    }

    private void processNextChunk(ResetJobImpl job,
            World world,
            List<ChunkDiffData> chunks,
            int[] chunkIndex,
            CompletableFuture<Void> future) {

        int currentIndex = chunkIndex[0];
        ChunkDiffData chunkData = chunks.get(currentIndex);

        scheduler.runOnChunk(world, chunkData.chunkX(), chunkData.chunkZ(), () -> {
            try {
                if (!world.isChunkLoaded(chunkData.chunkX(), chunkData.chunkZ())) {
                    world.loadChunk(chunkData.chunkX(), chunkData.chunkZ(), true);
                }

                for (BlockChange change : chunkData.changes()) {
                    BlockRecord record = change.record();
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
                logger.error("Error aplicando batch DIFF para zona '{}' chunk ({},{}): {}", e, job.getZone().getId(), chunkData.chunkX(), chunkData.chunkZ(), e.getMessage());
                future.completeExceptionally(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> verifyAndCleanup(ResetJobImpl job, World world, Object ctx) {
        return entityCleaner.cleanZone(world, job.getZone());
    }
}
