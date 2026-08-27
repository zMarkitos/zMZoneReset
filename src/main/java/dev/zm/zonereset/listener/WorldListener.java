// file: WorldListener.java
// description: Handles world unload events to clean up zone references and spatial indices.

package dev.zm.zonereset.listener;

import dev.zm.zonereset.api.zone.Zone;
import dev.zm.zonereset.zone.ZoneManagerImpl;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.Objects;
import java.util.UUID;

public final class WorldListener implements Listener {

    private final ZoneManagerImpl zoneManager;

    public WorldListener(ZoneManagerImpl zoneManager) {
        this.zoneManager = Objects.requireNonNull(zoneManager, "zoneManager");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        UUID worldUID = event.getWorld().getUID();
        zoneManager.handleWorldUnload(worldUID);
    }
}