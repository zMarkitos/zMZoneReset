package dev.zm.zonereset.listener;

import dev.zm.zonereset.api.zone.Zone;
import dev.zm.zonereset.diff.DiffTracker;
import dev.zm.zonereset.zone.ZoneManagerImpl;
import dev.zm.zonereset.zone.ZoneStatus;
import dev.zm.zonereset.zone.PlayerResetAction;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class BlockChangeListener implements Listener {

    private final ZoneManagerImpl zoneManager;
    private final DiffTracker diffTracker;

    public BlockChangeListener(ZoneManagerImpl zoneManager, DiffTracker diffTracker) {
        this.zoneManager = Objects.requireNonNull(zoneManager, "zoneManager");
        this.diffTracker = Objects.requireNonNull(diffTracker, "diffTracker");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        handleBlockChange(event, block, event.getBlock().getState(), event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        handleBlockChange(event, block, event.getBlockReplacedState(), event.getPlayer());
    }

    private void handleBlockChange(org.bukkit.event.Cancellable event,
            Block block,
            org.bukkit.block.BlockState oldState,
            Player player) {
        UUID worldUID = block.getWorld().getUID();
        int cx = block.getChunk().getX();
        int cz = block.getChunk().getZ();
        Set<Zone> zones = zoneManager.getZonesAt(worldUID, cx, cz);

        if (zones.isEmpty())
            return;

        for (Zone zone : zones) {
            if (!zone.contains(block.getX(), block.getY(), block.getZ())) {
                continue;
            }

            ZoneStatus status = zone.getStatus();

            if (status == ZoneStatus.RESETTING) {
                PlayerResetAction action = zone.getPlayerResetAction();
                if (action == PlayerResetAction.WARN_AND_BLOCK
                        || action == PlayerResetAction.TELEPORT) {
                    event.setCancelled(true);
                    if (player != null) {
                        player.sendMessage("§c[zMZoneReset] Esta zona está siendo restaurada. " +
                                "No puedes modificar bloques en este momento.");
                    }
                    return;
                }
            }

            if (status == ZoneStatus.READY) {
                diffTracker.trackChange(zone, oldState);
            }
        }
    }
}
