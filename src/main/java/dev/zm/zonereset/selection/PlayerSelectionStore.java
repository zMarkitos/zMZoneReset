package dev.zm.zonereset.selection;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerSelectionStore {

    private final Map<UUID, PlayerSelection> selections = new ConcurrentHashMap<>();

    public PlayerSelection getSelection(UUID playerId) {
        return selections.computeIfAbsent(playerId, k -> new PlayerSelection());
    }

    public void clearSelection(UUID playerId) {
        selections.remove(playerId);
    }
}
