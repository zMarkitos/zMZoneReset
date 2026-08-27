package dev.zm.zonereset.api.event;

import dev.zm.zonereset.api.reset.ResetJob;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Evento disparado cuando un reseteo de zona se completa exitosamente.
 */
public class ZoneResetCompleteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    
    private final ResetJob job;

    public ZoneResetCompleteEvent(ResetJob job) {
        this.job = job;
    }

    public ResetJob getJob() {
        return job;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
