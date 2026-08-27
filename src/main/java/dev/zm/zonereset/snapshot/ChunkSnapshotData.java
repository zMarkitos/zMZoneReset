package dev.zm.zonereset.snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ChunkSnapshotData {

    private final int chunkX;
    private final int chunkZ;
    private final List<BlockRecord> blocks;

    public ChunkSnapshotData(int chunkX, int chunkZ, List<BlockRecord> blocks) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.blocks = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(blocks, "blocks")));
    }

    public int getChunkX()             { return chunkX; }
    public int getChunkZ()             { return chunkZ; }
    public List<BlockRecord> getBlocks() { return blocks; }
    public int getBlockCount()         { return blocks.size(); }

    @Override
    public String toString() {
        return "ChunkSnapshotData{chunk=(" + chunkX + "," + chunkZ + ")" +
               ", blocks=" + blocks.size() + "}";
    }
}
