package dev.zm.zonereset.api.event;

import dev.zm.zonereset.api.reset.ResetJob;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Evento disparado cuando un reseteo de zona falla inesperadamente.
 */
public class ZoneResetFailedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final ResetJob job;
    private final Throwable exception;

    public ZoneResetFailedEvent(ResetJob job, Throwable exception) {
        this.job = job;
        this.exception = exception;
    }

    public ResetJob getJob() {
        return job;
    }

    public Throwable getException() {
        return exception;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
