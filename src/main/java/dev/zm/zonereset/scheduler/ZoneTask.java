package dev.zm.zonereset.scheduler;

public interface ZoneTask {
    void cancel();

    boolean isCancelled();

    ZoneTask NOOP = new ZoneTask() {
        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return true;
        }
    };
}
