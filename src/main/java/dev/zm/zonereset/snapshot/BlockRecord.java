package dev.zm.zonereset.snapshot;

import java.util.Arrays;
import java.util.Objects;

public final class BlockRecord {

    public final long packedPos;

    public final String blockDataString;

    public final byte[] blockEntityNbt;

    public BlockRecord(long packedPos, String blockDataString, byte[] blockEntityNbt) {
        this.packedPos = packedPos;
        this.blockDataString = Objects.requireNonNull(blockDataString, "blockDataString");
        this.blockEntityNbt = blockEntityNbt;
    }

    public boolean hasBlockEntity() {
        return blockEntityNbt != null && blockEntityNbt.length > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof BlockRecord other))
            return false;
        return packedPos == other.packedPos
                && blockDataString.equals(other.blockDataString)
                && Arrays.equals(blockEntityNbt, other.blockEntityNbt);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(packedPos);
    }

    @Override
    public String toString() {
        return "BlockRecord{pos=" + packedPos +
                ", data='" + blockDataString + "'" +
                ", hasNBT=" + hasBlockEntity() + "}";
    }
}
