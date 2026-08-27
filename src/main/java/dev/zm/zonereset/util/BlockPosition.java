package dev.zm.zonereset.util;

public record BlockPosition(int x, int y, int z) {

    public static BlockPosition of(org.bukkit.block.Block block) {
        return new BlockPosition(block.getX(), block.getY(), block.getZ());
    }

    public static BlockPosition of(int x, int y, int z) {
        return new BlockPosition(x, y, z);
    }

    public static BlockPosition unpack(long packed) {
        int x = (int) (packed >> 38);
        int z = (int) ((packed << 26) >> 38);
        int y = (int) ((packed << 52) >> 52);
        return new BlockPosition(x, y, z);
    }

    public static int unpackX(long packed) {
        return (int) (packed >> 38);
    }

    public static int unpackY(long packed) {
        return (int) ((packed << 52) >> 52);
    }

    public static int unpackZ(long packed) {
        return (int) ((packed << 26) >> 38);
    }

    public long packed() {
        return pack(x, y, z);
    }

    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFFL) << 38)
                | ((long) (z & 0x3FFFFFFL) << 12)
                | (long) (y & 0xFFFL);
    }

    public ChunkCoordinate toChunk() {
        return ChunkCoordinate.of(x >> 4, z >> 4);
    }

    @Override
    public String toString() {
        return "Block(" + x + ", " + y + ", " + z + ")";
    }
}
