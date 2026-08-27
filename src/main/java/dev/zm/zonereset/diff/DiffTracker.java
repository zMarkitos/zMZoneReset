// file: DiffTracker.java
// description: Tracks block changes for DIFF strategy, captures original state before modifications.

package dev.zm.zonereset.diff;

import dev.zm.zonereset.api.zone.Zone;
import dev.zm.zonereset.core.logging.ZoneLogger;
import dev.zm.zonereset.snapshot.BlockRecord;
import dev.zm.zonereset.snapshot.BlockEntitySerializer;
import dev.zm.zonereset.util.BlockPosition;
import dev.zm.zonereset.zone.ZoneStatus;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;

import java.util.Objects;
import java.util.Set;

public final class DiffTracker {

    private final DiffStore diffStore;
    private final BlockEntitySerializer entitySerializer;
    private final ZoneLogger logger;

    public DiffTracker(DiffStore diffStore,
            BlockEntitySerializer entitySerializer,
            ZoneLogger logger) {
        this.diffStore = Objects.requireNonNull(diffStore, "diffStore");
        this.entitySerializer = Objects.requireNonNull(entitySerializer, "entitySerializer");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void trackChange(Zone zone, org.bukkit.block.BlockState state) {
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(state, "state");

        if (zone.getStatus() != ZoneStatus.READY) {
            return;
        }
        if (!zone.contains(state.getX(), state.getY(), state.getZ())) {
            return;
        }

        long packedPos = BlockPosition.pack(state.getX(), state.getY(), state.getZ());
        String zoneId = zone.getId();

        if (diffStore.isRegistered(zoneId, packedPos)) {
            return;
        }

        String blockDataStr = state.getBlockData().getAsString();
        byte[] nbt = null;
        if (state instanceof TileState) {
            nbt = entitySerializer.captureBlockEntity(state.getBlock());
        }

        BlockRecord original = new BlockRecord(packedPos, blockDataStr, nbt);
        boolean isNew = diffStore.recordIfAbsent(zoneId, original);

        if (isNew) {
            logger.debug("DIFF: bloque registrado en '{}' en ({},{},{}): {}",
                    zoneId, state.getX(), state.getY(), state.getZ(), blockDataStr);
        }
    }

    public void trackExplosion(Set<Zone> zones, java.util.List<Block> blocks) {
        if (zones.isEmpty() || blocks.isEmpty())
            return;

        for (Block block : blocks) {
            for (Zone zone : zones) {
                if (zone.getStatus() == ZoneStatus.READY
                        && zone.contains(block.getX(), block.getY(), block.getZ())) {
                    trackChange(zone, block.getState());
                }
            }
        }
    }

    public int getChangeCount(String zoneId) {
        return diffStore.getChangeCount(zoneId);
    }

    public void clearZone(String zoneId) {
        diffStore.clearZone(zoneId);
    }

    public void clearAll() {
        diffStore.clearAll();
    }
}