package dev.zm.zonereset.storage;

import dev.zm.zonereset.snapshot.ZoneSnapshot;

import java.util.Optional;

public interface SnapshotStorage {

    void saveSnapshot(String zoneId, ZoneSnapshot snapshot) throws StorageException;

    Optional<ZoneSnapshot> loadSnapshot(String zoneId) throws StorageException;

    boolean hasSnapshot(String zoneId);

    void deleteSnapshot(String zoneId) throws StorageException;

    StorageInfo getInfo(String zoneId);

    class StorageException extends Exception {
        public StorageException(String message) {
            super(message);
        }

        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
