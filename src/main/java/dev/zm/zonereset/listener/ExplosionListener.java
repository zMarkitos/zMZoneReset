package dev.zm.zonereset.listener;

import dev.zm.zonereset.api.zone.Zone;
import dev.zm.zonereset.diff.DiffTracker;
import dev.zm.zonereset.zone.ZoneManagerImpl;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ExplosionListener implements Listener {

    private final ZoneManagerImpl zoneManager;
    private final DiffTracker diffTracker;

    public ExplosionListener(ZoneManagerImpl zoneManager, DiffTracker diffTracker) {
        this.zoneManager = Objects.requireNonNull(zoneManager, "zoneManager");
        this.diffTracker = Objects.requireNonNull(diffTracker, "diffTracker");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosion(event.getEntity().getWorld().getUID(), event.blockList());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.getBlock().getWorld().getUID(), event.blockList());
    }

    private void handleExplosion(UUID worldUID, List<Block> affectedBlocks) {
        if (affectedBlocks.isEmpty())
            return;

        Set<Zone> affectedZones = new HashSet<>();
        // Explosions usually touch many blocks in the same few chunks. Cache
        // the spatial lookup per chunk and avoid Block#getChunk() entirely.
        Map<Long, Set<Zone>> chunkCache = new HashMap<>();

        for (Block block : affectedBlocks) {
            int cx = block.getX() >> 4;
            int cz = block.getZ() >> 4;
            long chunkKey = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
            Set<Zone> zonesAtChunk = chunkCache.get(chunkKey);
            if (zonesAtChunk == null) {
                zonesAtChunk = zoneManager.getZonesAt(worldUID, cx, cz);
                chunkCache.put(chunkKey, zonesAtChunk);
            }
            if (!zonesAtChunk.isEmpty()) {
                affectedZones.addAll(zonesAtChunk);
            }
        }

        if (affectedZones.isEmpty())
            return;
        diffTracker.trackExplosion(affectedZones, affectedBlocks);
    }
}
