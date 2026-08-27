package dev.zm.zonereset.zone;

import dev.zm.zonereset.util.ChunkCoordinate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ZoneBounds {

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    private volatile List<ChunkCoordinate> chunksCache;

    public ZoneBounds(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public int getSizeX() {
        return maxX - minX + 1;
    }

    public int getSizeY() {
        return maxY - minY + 1;
    }

    public int getSizeZ() {
        return maxZ - minZ + 1;
    }

    public long getVolume() {
        return (long) getSizeX() * getSizeY() * getSizeZ();
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public boolean contains(long packedPos) {
        int x = (int) (packedPos >> 38);
        int z = (int) ((packedPos << 26) >> 38);
        int y = (int) (packedPos << 52) >> 52;
        return contains(x, y, z);
    }

    public boolean intersectsChunk(int chunkX, int chunkZ) {
        int chunkMinX = chunkX << 4;
        int chunkMaxX = chunkMinX + 15;
        int chunkMinZ = chunkZ << 4;
        int chunkMaxZ = chunkMinZ + 15;

        return chunkMaxX >= minX && chunkMinX <= maxX
                && chunkMaxZ >= minZ && chunkMinZ <= maxZ;
    }

    public List<ChunkCoordinate> getChunks() {
        List<ChunkCoordinate> result = chunksCache;
        if (result == null) {
            result = calculateChunks();
            chunksCache = result;
        }
        return result;
    }

    public int getChunkCount() {
        int minCX = minX >> 4;
        int maxCX = maxX >> 4;
        int minCZ = minZ >> 4;
        int maxCZ = maxZ >> 4;
        return (maxCX - minCX + 1) * (maxCZ - minCZ + 1);
    }

    private List<ChunkCoordinate> calculateChunks() {
        int minCX = minX >> 4;
        int maxCX = maxX >> 4;
        int minCZ = minZ >> 4;
        int maxCZ = maxZ >> 4;

        List<ChunkCoordinate> chunks = new ArrayList<>((maxCX - minCX + 1) * (maxCZ - minCZ + 1));
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                chunks.add(ChunkCoordinate.of(cx, cz));
            }
        }
        return Collections.unmodifiableList(chunks);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ZoneBounds other))
            return false;
        return minX == other.minX && minY == other.minY && minZ == other.minZ
                && maxX == other.maxX && maxY == other.maxY && maxZ == other.maxZ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public String toString() {
        return "ZoneBounds[(" + minX + "," + minY + "," + minZ + ")" +
                " → (" + maxX + "," + maxY + "," + maxZ + ")" +
                " vol=" + getVolume() + " chunks=" + getChunkCount() + "]";
    }
}
