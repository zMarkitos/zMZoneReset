package dev.zm.zonereset.util;

public record ChunkCoordinate(int x, int z) {

    public static ChunkCoordinate of(org.bukkit.Chunk chunk) {
        return new ChunkCoordinate(chunk.getX(), chunk.getZ());
    }

    public static ChunkCoordinate of(int chunkX, int chunkZ) {
        return new ChunkCoordinate(chunkX, chunkZ);
    }

    public static ChunkCoordinate fromBlock(int blockX, int blockZ) {
        return new ChunkCoordinate(blockX >> 4, blockZ >> 4);
    }

    public static ChunkCoordinate unpack(long packed) {
        int cx = (int) (packed >> 32);
        int cz = (int) (packed & 0xFFFFFFFFL);
        return new ChunkCoordinate(cx, cz);
    }

    public long packed() {
        return pack(x, z);
    }

    public static long pack(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    @Override
    public String toString() {
        return "Chunk(" + x + ", " + z + ")";
    }
}
