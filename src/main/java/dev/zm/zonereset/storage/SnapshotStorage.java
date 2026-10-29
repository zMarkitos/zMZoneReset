package dev.zm.zonereset.storage;

import dev.zm.zonereset.snapshot.ChunkSnapshotData;
import dev.zm.zonereset.snapshot.SnapshotStreamWriter;
import dev.zm.zonereset.snapshot.ZoneSnapshot;

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public interface SnapshotStorage {

    void saveSnapshot(String zoneId, ZoneSnapshot snapshot) throws StorageException;

    Optional<ZoneSnapshot> loadSnapshot(String zoneId) throws StorageException;

    boolean hasSnapshot(String zoneId);

    void deleteSnapshot(String zoneId) throws StorageException;

    StorageInfo getInfo(String zoneId);

    SnapshotStreamWriter beginStreamingCapture(String zoneId, UUID worldUID, int chunkCount)
            throws IOException;

    Optional<SnapshotStreamReader> openStreamingReader(String zoneId) throws StorageException;

    interface SnapshotStreamReader extends Closeable {

        int getTotalChunks();

        long getTotalBlocks();

        Optional<ChunkSnapshotData> readNextChunk() throws IOException;

        int getChunksRead();

        @Override
        void close() throws IOException;
    }

    class StorageException extends Exception {
        public StorageException(String message) {
            super(message);
        }

        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
