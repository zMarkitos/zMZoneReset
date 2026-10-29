package dev.zm.zonereset.reset;

import dev.zm.zonereset.api.reset.ResetJob;

import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ResetQueue {

    private final Queue<ResetJobImpl> queue = new ConcurrentLinkedQueue<>();
    /** O(1) duplicate detection instead of scanning the whole queue. */
    private final Set<String> queuedZoneIds = ConcurrentHashMap.newKeySet();

    public void enqueue(ResetJobImpl job) {
        queuedZoneIds.add(job.getZone().getId());
        queue.offer(job);
    }

    public Optional<ResetJobImpl> poll() {
        ResetJobImpl job = queue.poll();
        if (job == null) {
            return Optional.empty();
        }
        queuedZoneIds.remove(job.getZone().getId());
        return Optional.of(job);
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int size() {
        return queue.size();
    }

    public void clear() {
        queue.clear();
        queuedZoneIds.clear();
    }

    public boolean isQueued(String zoneId) {
        return zoneId != null && queuedZoneIds.contains(zoneId);
    }
}
