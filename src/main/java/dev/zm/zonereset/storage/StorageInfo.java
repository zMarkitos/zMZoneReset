package dev.zm.zonereset.storage;

import java.time.Instant;
import java.util.Objects;

public record StorageInfo(
        String zoneId,
        boolean exists,
        long sizeBytes,
        int chunkCount,
        long totalBlocks,
        Instant capturedAt,
        long checksumCRC32,
        String compressionAlgorithm) {
    public static StorageInfo notFound(String zoneId) {
        Objects.requireNonNull(zoneId, "zoneId");
        return new StorageInfo(zoneId, false, 0L, 0, 0L, null, 0L, "NONE");
    }

    public String getHumanSize() {
        if (sizeBytes < 1024)
            return sizeBytes + " B";
        if (sizeBytes < 1_048_576)
            return String.format("%.1f KB", sizeBytes / 1024.0);
        if (sizeBytes < 1_073_741_824)
            return String.format("%.2f MB", sizeBytes / 1_048_576.0);
        return String.format("%.2f GB", sizeBytes / 1_073_741_824.0);
    }
}
