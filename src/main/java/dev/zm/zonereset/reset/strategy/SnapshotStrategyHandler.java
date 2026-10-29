package dev.zm.zonereset.reset.strategy;

import dev.zm.zonereset.core.logging.ZoneLogger;
import dev.zm.zonereset.reset.ResetJobImpl;
import dev.zm.zonereset.scheduler.ZoneScheduler;
import dev.zm.zonereset.snapshot.BlockEntitySerializer;
import dev.zm.zonereset.snapshot.BlockRecord;
import dev.zm.zonereset.snapshot.ChunkSnapshotData;
import dev.zm.zonereset.storage.SnapshotStorage;
import dev.zm.zonereset.storage.SnapshotStorage.SnapshotStreamReader;
import dev.zm.zonereset.util.BlockPosition;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Estrategia de reset basada en snapshot completo.
 *
 * <p>Usa un {@link SnapshotStreamReader} para leer el snapshot chunk a chunk
 * directamente desde disco, sin cargar todo en RAM.  Esto permite restaurar
 * zonas de cualquier tamaño sin riesgo de OutOfMemoryError.
 *
 * <p>Pipeline de ejecución:
 * <ol>
 *   <li>{@code prepareAsync} — verifica que existe un snapshot válido y abre el reader.</li>
 *   <li>{@code executeReset} — restaura cada chunk en su propio tick del servidor,
 *       cerrando el reader al finalizar.</li>
 *   <li>{@code verifyAndCleanup} — limpia entidades residuales de la zona.</li>
 * </ol>
 */
public final class SnapshotStrategyHandler implements ResetStrategyHandler {

    /**
     * Upper bound of block writes per tick while restoring a chunk. A single
     * chunk can contain tens of thousands of blocks (full-height zones), so
     * applying it in one tick was a guaranteed lag spike.
     */
    private static final int BLOCKS_PER_TICK = 2_000;

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

    // -------------------------------------------------------------------------
    // Contexto interno: envuelve el reader de streaming
    // -------------------------------------------------------------------------

    /** Contexto pasado de prepareAsync → executeReset. */
    private record SnapshotContext(SnapshotStreamReader reader) {}

    // -------------------------------------------------------------------------
    // prepareAsync: verifica y abre el streaming reader en hilo async
    // -------------------------------------------------------------------------

    @Override
    public CompletableFuture<Object> prepareAsync(ResetJobImpl job) {
        CompletableFuture<Object> future = new CompletableFuture<>();
        String zoneId = job.getZone().getId();

        scheduler.runAsync(() -> {
            try {
                // Verificación rápida antes de abrir el reader
                if (!storage.hasSnapshot(zoneId)) {
                    future.completeExceptionally(new IllegalStateException(
                            "No se encontró un snapshot para la zona '" + zoneId + "'."));
                    return;
                }

                Optional<SnapshotStreamReader> optReader = storage.openStreamingReader(zoneId);

                if (optReader.isEmpty()) {
                    future.completeExceptionally(new IllegalStateException(
                            "No se pudo abrir el snapshot streaming para la zona '" + zoneId + "'."));
                    return;
                }

                SnapshotStreamReader reader = optReader.get();

                if (reader.getTotalChunks() == 0) {
                    try { reader.close(); } catch (IOException ignored) {}
                    future.completeExceptionally(new RuntimeException(
                            "El snapshot de la zona '" + zoneId + "' está vacío (0 chunks)."));
                    return;
                }

                logger.info("SNAPSHOT prepare para '{}': {} chunks a restaurar desde disco (streaming).",
                        zoneId, reader.getTotalChunks());

                future.complete(new SnapshotContext(reader));

            } catch (Exception e) {
                logger.error("Error abriendo snapshot streaming para zona '{}': {}", e, zoneId, e.getMessage());
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    // -------------------------------------------------------------------------
    // executeReset: restaura chunk a chunk usando el streaming reader
    // -------------------------------------------------------------------------

    @Override
    public CompletableFuture<Void> executeReset(ResetJobImpl job, World world, Object ctx) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        SnapshotContext context = (SnapshotContext) ctx;
        SnapshotStreamReader reader = context.reader();

        if (reader == null) {
            future.completeExceptionally(new RuntimeException("Contexto de SNAPSHOT nulo."));
            return future;
        }

        processNextChunkFromStream(job, world, reader, future);
        return future;
    }

    /**
     * Lee el siguiente chunk del reader en hilo async y lo restaura en el hilo del servidor.
     * Recursivo en cola (tail-call via scheduler) para evitar stack overflow en zonas grandes.
     */
    private void processNextChunkFromStream(ResetJobImpl job,
            World world,
            SnapshotStreamReader reader,
            CompletableFuture<Void> future) {

        // Leer el siguiente chunk en hilo async (I/O + descompresión no bloquean el main thread)
        scheduler.runAsync(() -> {
            ChunkSnapshotData chunkData;
            try {
                Optional<ChunkSnapshotData> next = reader.readNextChunk();
                if (next.isEmpty()) {
                    // No quedan más chunks — éxito
                    closeReaderSilently(reader);
                    scheduler.runSync(() -> future.complete(null));
                    return;
                }
                chunkData = next.get();
            } catch (IOException e) {
                closeReaderSilently(reader);
                logger.error("Error leyendo chunk del snapshot stream para zona '{}': {}",
                        e, job.getZone().getId(), e.getMessage());
                scheduler.runSync(() -> future.completeExceptionally(e));
                return;
            }

            final ChunkSnapshotData finalChunk = chunkData;

            // Restaurar el chunk en el hilo del servidor (o del chunk en Folia).
            // The work is sliced across ticks so a full-height chunk never
            // monopolizes the main/region thread.
            scheduler.runOnChunk(world, finalChunk.getChunkX(), finalChunk.getChunkZ(), () ->
                    applyChunkSlice(job, world, reader, finalChunk, 0,
                            new HashMap<>(256), future));
        });
    }

    private void applyChunkSlice(ResetJobImpl job,
            World world,
            SnapshotStreamReader reader,
            ChunkSnapshotData chunk,
            int startIndex,
            Map<String, org.bukkit.block.data.BlockData> dataCache,
            CompletableFuture<Void> future) {

        try {
            int chunkX = chunk.getChunkX();
            int chunkZ = chunk.getChunkZ();

            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                world.loadChunk(chunkX, chunkZ, true);
            }

            List<BlockRecord> blocks = chunk.getBlocks();
            int endIndex = Math.min(startIndex + BLOCKS_PER_TICK, blocks.size());

            for (int i = startIndex; i < endIndex; i++) {
                BlockRecord record = blocks.get(i);
                int x = BlockPosition.unpackX(record.packedPos);
                int y = BlockPosition.unpackY(record.packedPos);
                int z = BlockPosition.unpackZ(record.packedPos);

                Block block = world.getBlockAt(x, y, z);
                org.bukkit.block.data.BlockData bd = dataCache.get(record.blockDataString);
                if (bd == null) {
                    bd = Bukkit.createBlockData(record.blockDataString);
                    dataCache.put(record.blockDataString, bd);
                }
                block.setBlockData(bd, false);

                // Only block entities need a forced state update. Doing this for
                // every block was one of the hottest paths of the reset.
                if (record.hasBlockEntity()) {
                    block.getState().update(true, false);
                    entitySerializer.restoreBlockEntity(block, record.blockEntityNbt);
                }
            }

            int total = reader.getTotalChunks();
            int completedChunks = Math.max(0, reader.getChunksRead() - 1);
            double chunkPart = blocks.isEmpty() ? 1.0 : (double) endIndex / blocks.size();
            double progress = total > 0 ? (completedChunks + chunkPart) / total : 1.0;
            job.setProgress(progress);

            if (endIndex < blocks.size()) {
                // Continue on the same chunk's region thread next tick.
                scheduler.runOnChunkLater(world, chunkX, chunkZ,
                        () -> applyChunkSlice(job, world, reader, chunk, endIndex, dataCache, future),
                        1L);
            } else {
                scheduler.runSyncLater(() ->
                        processNextChunkFromStream(job, world, reader, future), 1L);
            }

        } catch (Exception e) {
            closeReaderSilently(reader);
            logger.error("Error restaurando chunk ({},{}) para zona '{}': {}",
                    e, chunk.getChunkX(), chunk.getChunkZ(),
                    job.getZone().getId(), e.getMessage());
            future.completeExceptionally(e);
        }
    }

    private void closeReaderSilently(SnapshotStreamReader reader) {
        try { reader.close(); } catch (IOException ignored) {}
    }

    // -------------------------------------------------------------------------
    // verifyAndCleanup: limpieza de entidades post-reset
    // -------------------------------------------------------------------------

    @Override
    public CompletableFuture<Void> verifyAndCleanup(ResetJobImpl job, World world, Object ctx) {
        return entityCleaner.cleanZone(world, job.getZone());
    }
}
