package dev.zm.zonereset.diff;

import dev.zm.zonereset.snapshot.BlockRecord;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DiffStore {

    private final Map<String, Map<Long, BlockChange>> zoneChanges = new ConcurrentHashMap<>();

    public boolean recordIfAbsent(String zoneId, BlockRecord original) {
        Map<Long, BlockChange> changes = zoneChanges.computeIfAbsent(
                zoneId, $ -> new ConcurrentHashMap<>());
        long packed = original.packedPos;
        // Atomic check-and-put: avoids losing/duplicating records when an
        // explosion or multiple listeners race on the same position.
        return changes.putIfAbsent(packed, new BlockChange(original)) == null;
    }

    public int getChangeCount(String zoneId) {
        Map<Long, BlockChange> changes = zoneChanges.get(zoneId);
        return changes == null ? 0 : changes.size();
    }

    public boolean isRegistered(String zoneId, long packedPos) {
        Map<Long, BlockChange> changes = zoneChanges.get(zoneId);
        return changes != null && changes.containsKey(packedPos);
    }

    public Collection<BlockChange> getChanges(String zoneId) {
        Map<Long, BlockChange> changes = zoneChanges.get(zoneId);
        if (changes == null)
            return Collections.emptyList();
        return Collections.unmodifiableCollection(changes.values());
    }

    public boolean hasChanges(String zoneId) {
        Map<Long, BlockChange> changes = zoneChanges.get(zoneId);
        return changes != null && !changes.isEmpty();
    }

    public void clearZone(String zoneId) {
        zoneChanges.remove(zoneId);
    }

    public void clearAll() {
        zoneChanges.clear();
    }
}