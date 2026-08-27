package dev.zm.zonereset.reset.strategy;

import dev.zm.zonereset.api.zone.Zone;
import dev.zm.zonereset.scheduler.ZoneScheduler;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class EntityCleaner {

    private final ZoneScheduler scheduler;

    public EntityCleaner(ZoneScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public CompletableFuture<Void> cleanZone(World world, Zone zone) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        double minX = zone.getBounds().getMinX();
        double minY = zone.getBounds().getMinY();
        double minZ = zone.getBounds().getMinZ();
        double maxX = zone.getBounds().getMaxX();
        double maxY = zone.getBounds().getMaxY();
        double maxZ = zone.getBounds().getMaxZ();

        double centerX = minX + (maxX - minX) / 2.0;
        double centerY = minY + (maxY - minY) / 2.0;
        double centerZ = minZ + (maxZ - minZ) / 2.0;

        int chunkX = ((int) Math.floor(centerX)) >> 4;
        int chunkZ = ((int) Math.floor(centerZ)) >> 4;

        scheduler.runOnChunk(world, chunkX, chunkZ, () -> {
            try {
                double rx = (maxX - minX) / 2.0;
                double ry = (maxY - minY) / 2.0;
                double rz = (maxZ - minZ) / 2.0;

                org.bukkit.Location center = new org.bukkit.Location(world, centerX, centerY, centerZ);

                Collection<Entity> entities = world.getNearbyEntities(center, rx, ry, rz);

                for (Entity entity : entities) {
                    if (entity instanceof Player)
                        continue;

                    if (entity instanceof Item || entity instanceof Projectile) {
                        entity.remove();
                    }
                }

                future.complete(null);

            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }
}
