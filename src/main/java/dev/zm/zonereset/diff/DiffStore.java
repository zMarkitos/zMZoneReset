// file: DiffStore.java
// description: Stores block changes per zone for the DIFF reset strategy. Tracks original block states.

package dev.zm.zonereset.diff;

import dev.zm.zonereset.snapshot.BlockRecord;

import java.util.*;

public final class DiffStore {

    private final Map<String, Map<Long, BlockChange>> zoneChanges = new HashMap<>();

    public boolean recordIfAbsent(String zoneId, BlockRecord original) {
        Map<Long, BlockChange> changes = zoneChanges.computeIfAbsent(
                zoneId, $ -> new HashMap<>());
        long packed = original.packedPos;
        if (changes.containsKey(packed)) {
            return false;
        }
        changes.put(packed, new BlockChange(original));
        return true;
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