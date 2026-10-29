package dev.zm.zonereset.reset.strategy;

import dev.zm.zonereset.api.zone.Zone;
import dev.zm.zonereset.scheduler.ZoneScheduler;
import dev.zm.zonereset.util.ChunkCoordinate;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class EntityCleaner {

    private final ZoneScheduler scheduler;

    public EntityCleaner(ZoneScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public CompletableFuture<Void> cleanZone(World world, Zone zone) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        List<ChunkCoordinate> chunks = zone.getBounds().getChunks();
        cleanChunk(world, zone, chunks, 0, future);
        return future;
    }

    /**
     * Cleans one chunk at a time on its owning region thread. This replaces the
     * old cross-region {@code world.getNearbyEntities(...)} call, which is not
     * safe on Folia and could also miss entities in distant chunks.
     */
    private void cleanChunk(World world, Zone zone, List<ChunkCoordinate> chunks,
            int index, CompletableFuture<Void> future) {

        if (index >= chunks.size()) {
            future.complete(null);
            return;
        }

        ChunkCoordinate coord = chunks.get(index);
        scheduler.runOnChunk(world, coord.x(), coord.z(), () -> {
            try {
                if (!world.isChunkLoaded(coord.x(), coord.z())) {
                    world.loadChunk(coord.x(), coord.z(), true);
                }

                Chunk chunk = world.getChunkAt(coord.x(), coord.z());
                for (Entity entity : chunk.getEntities()) {
                    if (entity instanceof Player) {
                        continue;
                    }
                    if (!(entity instanceof Item || entity instanceof Projectile)) {
                        continue;
                    }

                    org.bukkit.Location location = entity.getLocation();
                    if (zone.contains(location.getBlockX(), location.getBlockY(), location.getBlockZ())) {
                        entity.remove();
                    }
                }

                // One chunk per tick keeps post-reset cleanup smooth on both
                // Paper and Folia.
                scheduler.runSyncLater(() -> cleanChunk(world, zone, chunks, index + 1, future), 1L);

            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
    }
}
