package dev.zm.zonereset.snapshot;

import dev.zm.zonereset.core.logging.ZoneLogger;
import dev.zm.zonereset.scheduler.ZoneScheduler;
import dev.zm.zonereset.storage.SnapshotStorage;
import dev.zm.zonereset.util.ChunkCoordinate;
import dev.zm.zonereset.zone.ZoneBounds;
import dev.zm.zonereset.zone.ZoneImpl;
import dev.zm.zonereset.zone.ZoneManagerImpl;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class SnapshotCapture {
    private static final Set<String> ACTIVE_CAPTURES = ConcurrentHashMap.newKeySet();
    private static final String AIR_BLOCK_DATA = "minecraft:air";

    private final ZoneScheduler scheduler;
    private final SnapshotStorage storage;
    private final BlockEntitySerializer entitySerializer;
    private final ZoneManagerImpl zoneManager;
    private final ZoneLogger logger;

    private volatile int chunksPerTick;

    public SnapshotCapture(ZoneScheduler scheduler,
            SnapshotStorage storage,
            BlockEntitySerializer entitySerializer,
            ZoneManagerImpl zoneManager,
            ZoneLogger logger) {
        this(scheduler, storage, entitySerializer, zoneManager, logger, 1);
    }

    public SnapshotCapture(ZoneScheduler scheduler,
            SnapshotStorage storage,
            BlockEntitySerializer entitySerializer,
            ZoneManagerImpl zoneManager,
            ZoneLogger logger,
            int chunksPerTick) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.entitySerializer = Objects.requireNonNull(entitySerializer, "entitySerializer");
        this.zoneManager = Objects.requireNonNull(zoneManager, "zoneManager");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.chunksPerTick = Math.max(1, chunksPerTick);
    }

    public void setChunksPerTick(int chunksPerTick) {
        this.chunksPerTick = Math.max(1, chunksPerTick);
    }

    public int getChunksPerTick() {
        return chunksPerTick;
    }

    public boolean isCapturing(String zoneId) {
        return ACTIVE_CAPTURES.contains(zoneId);
    }

    public CompletableFuture<Void> beginCapture(ZoneImpl zone,
            World world,
            Consumer<Double> onProgress) {
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(world, "world");

        if (!ACTIVE_CAPTURES.add(zone.getId())) {
            CompletableFuture<Void> rejected = new CompletableFuture<>();
            rejected.completeExceptionally(new IllegalStateException(
                    "Zone '" + zone.getId() + "' already has an active capture in progress."));
            return rejected;
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        ZoneBounds bounds = zone.getBounds();
        List<ChunkCoordinate> chunks = bounds.getChunks();

        logger.info("Starting snapshot capture for zone '{}'. {} chunks to process.",
                zone.getId(), chunks.size());

        scheduler.runAsync(() -> {
            SnapshotStreamWriter streamWriter;
            try {
                streamWriter = storage.beginStreamingCapture(zone.getId(), world.getUID(),
                        chunks.size());
            } catch (Exception e) {
                ACTIVE_CAPTURES.remove(zone.getId());
                logger.error("Error opening snapshot stream for zone '{}': {}",
                        e, zone.getId(), e.getMessage());
                scheduler.runSync(() -> future.completeExceptionally(e));
                return;
            }

            scheduler.runSync(() -> processNextBatch(zone, world, bounds, chunks, 0,
                    streamWriter, future, onProgress));
        });

        return future;
    }

    private void processNextBatch(ZoneImpl zone,
            World world,
            ZoneBounds bounds,
            List<ChunkCoordinate> chunks,
            int startIdx,
            SnapshotStreamWriter streamWriter,
            CompletableFuture<Void> future,
            Consumer<Double> onProgress) {

        if (startIdx >= chunks.size()) {
            finalizeCaptureAsync(zone, streamWriter, future);
            return;
        }

        int endIdx = Math.min(startIdx + chunksPerTick, chunks.size());
        int batchSize = endIdx - startIdx;

        // Captured chunks are produced on their owning region thread (Folia) and
        // collected here before a single async disk write for the whole batch.
        List<ChunkSnapshotData> batch = Collections.synchronizedList(new ArrayList<>(batchSize));
        AtomicInteger remaining = new AtomicInteger(batchSize);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int i = startIdx; i < endIdx; i++) {
            final int index = i;
            final ChunkCoordinate coord = chunks.get(i);

            // On Paper this runs on the main thread; on Folia it runs on the
            // region that owns the chunk, which is required for all block access.
            scheduler.runOnChunk(world, coord.x(), coord.z(), () -> {
                if (failed.get()) {
                    if (remaining.decrementAndGet() == 0) {
                        // Failure path already completed the future.
                    }
                    return;
                }

                try {
                    if (!world.isChunkLoaded(coord.x(), coord.z())) {
                        world.loadChunk(coord.x(), coord.z(), true);
                    }
                    Chunk chunk = world.getChunkAt(coord.x(), coord.z());
                    ChunkSnapshotData data = captureChunk(chunk, bounds);
                    batch.add(data);

                    logger.debug("Captured chunk {}/{} ({},{}) — {} blocks.",
                            index + 1, chunks.size(),
                            coord.x(), coord.z(),
                            data.getBlockCount());
                } catch (Exception e) {
                    if (failed.compareAndSet(false, true)) {
                        logger.error("Error capturing chunk ({},{}) of zone '{}': {}",
                                e, coord.x(), coord.z(), zone.getId(), e.getMessage());
                        closeStreamAndFail(zone, streamWriter, future, e, coord);
                    }
                } finally {
                    if (remaining.decrementAndGet() == 0 && !failed.get()) {
                        writeBatchAsync(zone, world, bounds, chunks, endIdx,
                                streamWriter, future, onProgress, batch);
                    }
                }
            });
        }
    }

    private void writeBatchAsync(ZoneImpl zone,
            World world,
            ZoneBounds bounds,
            List<ChunkCoordinate> chunks,
            int nextIdx,
            SnapshotStreamWriter streamWriter,
            CompletableFuture<Void> future,
            Consumer<Double> onProgress,
            List<ChunkSnapshotData> batch) {

        scheduler.runAsync(() -> {
            try {
                synchronized (batch) {
                    for (ChunkSnapshotData chunkData : batch) {
                        streamWriter.writeChunk(chunkData);
                    }
                }
            } catch (IOException e) {
                ACTIVE_CAPTURES.remove(zone.getId());
                logger.error("Error writing chunk batch for zone '{}': {}",
                        e, zone.getId(), e.getMessage());
                try {
                    streamWriter.abort();
                } catch (IOException ignored) {
                }
                scheduler.runSync(() -> future.completeExceptionally(e));
                return;
            }

            scheduler.runSync(() -> {
                if (onProgress != null) {
                    try {
                        onProgress.accept((double) nextIdx / chunks.size());
                    } catch (Exception ignored) {
                    }
                }

                // One tick of breathing room between batches keeps capture cheap
                // even when chunksPerTick is raised by the server owner.
                scheduler.runSyncLater(() -> processNextBatch(zone, world, bounds, chunks, nextIdx,
                        streamWriter, future, onProgress),
                        1L);
            });
        });
    }

    private ChunkSnapshotData captureChunk(Chunk chunk, ZoneBounds bounds) {
        int chunkMinX = chunk.getX() << 4;
        int chunkMinZ = chunk.getZ() << 4;

        int startX = Math.max(chunkMinX, bounds.getMinX());
        int endX = Math.min(chunkMinX + 15, bounds.getMaxX());
        int startZ = Math.max(chunkMinZ, bounds.getMinZ());
        int endZ = Math.min(chunkMinZ + 15, bounds.getMaxZ());
        int startY = bounds.getMinY();
        int endY = bounds.getMaxY();

        World world = chunk.getWorld();
        int expectedBlocks = (endX - startX + 1) * (endZ - startZ + 1) * (endY - startY + 1);
        List<BlockRecord> blocks = new ArrayList<>(expectedBlocks);

        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                for (int y = startY; y <= endY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    long packedPos = dev.zm.zonereset.util.BlockPosition.pack(x, y, z);

                    // Air is by far the most common block in most zones. Avoid the
                    // expensive BlockData stringification and BlockState creation.
                    Material type = block.getType();
                    if (type.isAir()) {
                        // Preserve cave_air/void_air exactly; only plain AIR can
                        // safely use the shared constant.
                        String airData = type == Material.AIR
                                ? AIR_BLOCK_DATA
                                : block.getBlockData().getAsString();
                        blocks.add(new BlockRecord(packedPos, airData, null));
                        continue;
                    }

                    String blockDataStr = block.getBlockData().getAsString();

                    byte[] nbt = null;
                    // getState(false) avoids a full snapshot copy; we only need the
                    // tile-state type and (for the serializer) its data.
                    BlockState state = block.getState(false);
                    if (state instanceof TileState) {
                        nbt = entitySerializer.captureBlockEntity(state);
                    }

                    blocks.add(new BlockRecord(packedPos, blockDataStr, nbt));
                }
            }
        }

        return new ChunkSnapshotData(chunk.getX(), chunk.getZ(), blocks);
    }

    private void finalizeCaptureAsync(ZoneImpl zone,
            SnapshotStreamWriter streamWriter,
            CompletableFuture<Void> future) {
        scheduler.runAsync(() -> {
            try {
                long totalBlocks = streamWriter.finish();

                if (totalBlocks == 0) {
                    ACTIVE_CAPTURES.remove(zone.getId());
                    scheduler.runSync(() -> {
                        zone.setSnapshotValid(false);
                        future.completeExceptionally(new RuntimeException(
                                "Snapshot did not capture any blocks. Check the zone coordinates."));
                    });
                    return;
                }

                logger.info("Snapshot for '{}' saved via streaming. {} total blocks.",
                        zone.getId(), totalBlocks);

                scheduler.runSync(() -> {
                    ACTIVE_CAPTURES.remove(zone.getId());
                    zone.setSnapshotValid(true);
                    future.complete(null);
                });

            } catch (Exception e) {
                ACTIVE_CAPTURES.remove(zone.getId());
                logger.error("Error finalizing snapshot for zone '{}': {}",
                        e, zone.getId(), e.getMessage());
                try {
                    streamWriter.abort();
                } catch (IOException ignored) {
                }
                scheduler.runSync(() -> {
                    zone.setSnapshotValid(false);
                    future.completeExceptionally(e);
                });
            }
        });
    }

    private void closeStreamAndFail(ZoneImpl zone,
            SnapshotStreamWriter streamWriter,
            CompletableFuture<Void> future,
            Exception cause,
            ChunkCoordinate failedChunk) {
        ACTIVE_CAPTURES.remove(zone.getId());
        scheduler.runAsync(() -> {
            try {
                streamWriter.abort();
            } catch (IOException ignored) {
            }
        });
        future.completeExceptionally(new RuntimeException(
                "Error capturing chunk " + failedChunk + " of zone '" + zone.getId() + "'",
                cause));
    }
}
