package dev.zm.zonereset.reset;

import dev.zm.zonereset.api.reset.ResetJob;

import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ResetQueue {

    private final Queue<ResetJobImpl> queue = new ConcurrentLinkedQueue<>();

    public void enqueue(ResetJobImpl job) {
        queue.offer(job);
    }

    public Optional<ResetJobImpl> poll() {
        return Optional.ofNullable(queue.poll());
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int size() {
        return queue.size();
    }

    public void clear() {
        queue.clear();
    }

    public boolean isQueued(String zoneId) {
        for (ResetJobImpl job : queue) {
            if (job.getZone().getId().equals(zoneId)) {
                return true;
            }
        }
        return false;
    }
}
