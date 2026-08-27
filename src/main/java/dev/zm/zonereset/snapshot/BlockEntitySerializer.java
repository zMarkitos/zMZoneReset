package dev.zm.zonereset.snapshot;

import dev.zm.zonereset.core.logging.ZoneLogger;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;

import java.util.Objects;

public final class BlockEntitySerializer {

    private final ZoneLogger logger;

    public BlockEntitySerializer(ZoneLogger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public byte[] captureBlockEntity(Block block) {
        Objects.requireNonNull(block, "block");

        BlockState state = block.getState();

        if (state instanceof TileState tileState) {
            return serializeTileState(tileState);
        }

        return null;
    }

    private byte[] serializeTileState(TileState tileState) {
        return null;
    }

    @SuppressWarnings("unchecked")
    public boolean restoreBlockEntity(Block block, byte[] nbtData) {
        if (nbtData == null || nbtData.length == 0)
            return true;
        Objects.requireNonNull(block, "block");

        BlockState state = block.getState();

        if (!(state instanceof TileState tileState)) {
            logger.warn("Block at {} is no longer a TileState after the reset. " +
                    "Block entity data could not be restored.",
                    locationString(block));
            return false;
        }

        try {
            return true;
        } catch (Exception e) {
            logger.warn("Error restoring block entity at {}: {}", locationString(block), e.getMessage());
            return false;
        }
    }

    private void applySerializedState(TileState tileState,
            java.util.Map<String, Object> serialized) {
    }

    private void applyContainerInventory(Container container,
            java.util.Map<String, Object> serialized) {
    }

    private static String locationString(TileState state) {
        return "(" + state.getX() + "," + state.getY() + "," + state.getZ() + ")";
    }

    private static String locationString(Block block) {
        return "(" + block.getX() + "," + block.getY() + "," + block.getZ() + ")";
    }
}
