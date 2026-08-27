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

import java.util.List;
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

        java.util.HashSet<Zone> affectedZones = new java.util.HashSet<>();

        for (Block block : affectedBlocks) {
            int cx = block.getChunk().getX();
            int cz = block.getChunk().getZ();
            Set<Zone> zonesAtChunk = zoneManager.getZonesAt(worldUID, cx, cz);
            affectedZones.addAll(zonesAtChunk);
        }

        if (affectedZones.isEmpty())
            return;
        diffTracker.trackExplosion(affectedZones, affectedBlocks);
    }
}
