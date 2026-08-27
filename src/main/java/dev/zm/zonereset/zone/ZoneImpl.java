package dev.zm.zonereset.zone;

import dev.zm.zonereset.api.reset.ResetStrategy;
import dev.zm.zonereset.api.zone.Zone;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementación interna de {@link Zone}.
 *
 * <p>
 * Inmutable en sus propiedades de identidad y geometría.
 * El estado ({@link ZoneStatus}) y el flag {@code snapshotValid}
 * son mutables y thread-safe mediante {@link AtomicReference} y
 * {@link AtomicBoolean}.
 *
 * <p>
 * Las mutaciones de estado solo deben realizarse desde el hilo del servidor
 * (o del scheduler apropiado para Folia) a través del {@link ZoneManagerImpl}.
 * El uso de AtomicReference garantiza visibilidad correcta incluso si en algún
 * contexto se lee desde otro hilo (por ejemplo, logs async).
 */
public final class ZoneImpl implements Zone {

    private final String id;
    private final UUID worldUID;
    private final ZoneBounds bounds;
    private final AtomicReference<PlayerResetAction> playerResetAction;

    private final AtomicLong resetIntervalTicks;
    private final AtomicLong remainingTicks;
    private final AtomicReference<ZoneStatus> status;
    private final AtomicBoolean snapshotValid;
    private final AtomicBoolean interactionBlocked;
    private final AtomicReference<org.bukkit.Location> spawn;
    private final AtomicReference<ResetStrategy> resetStrategy;
    private final AtomicBoolean showTitles;
    private final AtomicBoolean showMessages;

    // Constructor (via Builder)

    private ZoneImpl(Builder builder) {
        this.id = builder.id;
        this.worldUID = builder.worldUID;
        this.bounds = builder.bounds;
        this.playerResetAction = new AtomicReference<>(builder.playerResetAction);
        this.resetIntervalTicks = new AtomicLong(builder.resetIntervalTicks);
        this.remainingTicks = new AtomicLong(builder.resetIntervalTicks);
        this.status = new AtomicReference<>(
                builder.enabled ? ZoneStatus.READY : ZoneStatus.DISABLED);
        this.snapshotValid = new AtomicBoolean(builder.snapshotValid);
        this.interactionBlocked = new AtomicBoolean(builder.interactionBlocked);
        this.spawn = new AtomicReference<>(builder.spawn);
        this.resetStrategy = new AtomicReference<>(builder.resetStrategy);
        this.showTitles = new AtomicBoolean(builder.showTitles);
        this.showMessages = new AtomicBoolean(builder.showMessages);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public UUID getWorldUID() {
        return worldUID;
    }

    @Override
    public ZoneBounds getBounds() {
        return bounds;
    }

    @Override
    public ZoneStatus getStatus() {
        return status.get();
    }

    @Override
    public boolean isEnabled() {
        return status.get() != ZoneStatus.DISABLED;
    }

    @Override
    public ResetStrategy getResetStrategy() {
        return resetStrategy.get();
    }

    @Override
    public void setResetStrategy(ResetStrategy strategy) {
        this.resetStrategy.set(strategy);
    }

    @Override
    public PlayerResetAction getPlayerResetAction() {
        return playerResetAction.get();
    }

    @Override
    public void setPlayerResetAction(PlayerResetAction action) {
        this.playerResetAction.set(Objects.requireNonNull(action, "action"));
    }

    @Override
    public long getResetIntervalTicks() {
        return resetIntervalTicks.get();
    }

    @Override
    public void setResetIntervalTicks(long ticks) {
        this.resetIntervalTicks.set(ticks);
        this.remainingTicks.set(ticks);
    }

    @Override
    public long getRemainingTicks() {
        return remainingTicks.get();
    }

    @Override
    public void resetTimer() {
        this.remainingTicks.set(resetIntervalTicks.get());
    }

    public long decrementRemainingTicks(long amount) {
        return remainingTicks.addAndGet(-amount);
    }

    @Override
    public boolean isInteractionBlocked() {
        return interactionBlocked.get();
    }

    @Override
    public void setInteractionBlocked(boolean blocked) {
        this.interactionBlocked.set(blocked);
    }

    @Override
    public org.bukkit.Location getSpawn() {
        return spawn.get();
    }

    @Override
    public void setSpawn(org.bukkit.Location spawn) {
        this.spawn.set(spawn);
    }

    @Override
    public boolean isShowTitles() {
        return showTitles.get();
    }

    @Override
    public void setShowTitles(boolean show) {
        this.showTitles.set(show);
    }

    @Override
    public boolean isShowMessages() {
        return showMessages.get();
    }

    @Override
    public void setShowMessages(boolean show) {
        this.showMessages.set(show);
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return bounds.contains(x, y, z);
    }

    @Override
    public boolean contains(long packedPos) {
        return bounds.contains(packedPos);
    }

    @Override
    public boolean hasValidSnapshot() {
        return snapshotValid.get();
    }

    public void setStatus(ZoneStatus newStatus) {
        status.set(Objects.requireNonNull(newStatus, "newStatus"));
    }

    public void setSnapshotValid(boolean valid) {
        snapshotValid.set(valid);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ZoneImpl other))
            return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Zone{id='" + id + "'" +
                ", world=" + worldUID +
                ", bounds=" + bounds +
                ", status=" + status.get() +
                ", strategy=" + resetStrategy + "}";
    }

    public static Builder builder(String id, UUID worldUID, ZoneBounds bounds) {
        return new Builder(id, worldUID, bounds);
    }

    public static final class Builder {

        private final String id;
        private final UUID worldUID;
        private final ZoneBounds bounds;

        private ResetStrategy resetStrategy = ResetStrategy.AUTO;
        private long resetIntervalTicks = -1L;
        private PlayerResetAction playerResetAction = PlayerResetAction.WARN_AND_BLOCK;
        private boolean enabled = true;
        private boolean snapshotValid = false;
        private boolean interactionBlocked = false;
        private org.bukkit.Location spawn = null;
        private boolean showTitles = true;
        private boolean showMessages = true;

        private Builder(String id, UUID worldUID, ZoneBounds bounds) {
            this.id = Objects.requireNonNull(id, "id").strip();
            this.worldUID = Objects.requireNonNull(worldUID, "worldUID");
            this.bounds = Objects.requireNonNull(bounds, "bounds");
            if (this.id.isEmpty()) {
                throw new IllegalArgumentException("El ID de zona no puede estar vacío.");
            }
        }

        public Builder resetStrategy(ResetStrategy strategy) {
            this.resetStrategy = Objects.requireNonNull(strategy, "strategy");
            return this;
        }

        public Builder resetIntervalTicks(long ticks) {
            this.resetIntervalTicks = ticks < 0 ? -1L : ticks;
            return this;
        }

        public Builder playerResetAction(PlayerResetAction action) {
            this.playerResetAction = Objects.requireNonNull(action, "action");
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder snapshotValid(boolean valid) {
            this.snapshotValid = valid;
            return this;
        }

        public Builder interactionBlocked(boolean blocked) {
            this.interactionBlocked = blocked;
            return this;
        }

        public Builder spawn(org.bukkit.Location spawn) {
            this.spawn = spawn;
            return this;
        }

        public Builder showTitles(boolean show) {
            this.showTitles = show;
            return this;
        }

        public Builder showMessages(boolean show) {
            this.showMessages = show;
            return this;
        }

        public ZoneImpl build() {
            return new ZoneImpl(this);
        }
    }
}
