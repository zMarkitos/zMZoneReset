package dev.zm.zonereset.listener;

import dev.zm.zonereset.api.event.ZoneResetCompleteEvent;
import dev.zm.zonereset.api.event.ZoneResetFailedEvent;
import dev.zm.zonereset.api.event.ZoneResetStartEvent;
import dev.zm.zonereset.lang.ColorParser;
import dev.zm.zonereset.lang.LanguageManager;
import dev.zm.zonereset.scheduler.ZoneScheduler;
import dev.zm.zonereset.scheduler.ZoneTask;
import dev.zm.zonereset.zone.PlayerResetAction;
import dev.zm.zonereset.zone.ZoneImpl;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ResetEventListener implements Listener {

    private final JavaPlugin plugin;
    private final ZoneScheduler scheduler;
    private final LanguageManager langManager;
    private final Map<String, ZoneTask> reminderTasks = new ConcurrentHashMap<>();

    public ResetEventListener(JavaPlugin plugin, ZoneScheduler scheduler, LanguageManager langManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.langManager = Objects.requireNonNull(langManager, "langManager");
    }

    @EventHandler
    public void onResetStart(ZoneResetStartEvent event) {
        ZoneImpl zone = (ZoneImpl) event.getJob().getZone();
        if (!plugin.getConfig().getBoolean("reset-notifications.enabled", true)) {
            return;
        }

        cancelReminder(zone.getId());
        notifyPlayers(zone, "zones.broadcast.resetting", true, "start");
        if (zone.getPlayerResetAction() == PlayerResetAction.TELEPORT) {
            teleportPlayersOut(zone);
        }

        int intervalSeconds = Math.max(1, plugin.getConfig().getInt("reset-notifications.warning-interval-seconds", 60));
        ZoneTask task = scheduler.runSyncRepeating(() -> {
            if (zone.getStatus() != dev.zm.zonereset.zone.ZoneStatus.RESETTING) {
                cancelReminder(zone.getId());
                return;
            }
            notifyPlayers(zone, "zones.broadcast.resetting", false, "warning");
        }, intervalSeconds * 20L, intervalSeconds * 20L);
        reminderTasks.put(zone.getId(), task);
    }

    @EventHandler
    public void onResetComplete(ZoneResetCompleteEvent event) {
        ZoneImpl zone = (ZoneImpl) event.getJob().getZone();
        cancelReminder(zone.getId());
        notifyPlayers(zone, "zones.broadcast.reset-complete", false, "complete");
    }

    @EventHandler
    public void onResetFailed(ZoneResetFailedEvent event) {
        ZoneImpl zone = (ZoneImpl) event.getJob().getZone();
        cancelReminder(zone.getId());
    }

    private void notifyPlayers(ZoneImpl zone, String messageKey, boolean useTitle, String soundKey) {
        World world = Bukkit.getWorld(zone.getWorldUID());
        if (world == null) {
            return;
        }

        String message = langManager.getMessage(messageKey, "zone", zone.getId());
        String title = ColorParser.parse(plugin.getConfig().getString("reset-notifications.title.title", "<red><bold>Zone Reset</bold></red>"));
        String subtitle = ColorParser.parse(plugin.getConfig().getString("reset-notifications.title.subtitle", "<yellow>{zone}</yellow> <gray>is resetting</gray>").replace("{zone}", zone.getId()));
        Sound sound = parseSound(plugin.getConfig().getString("reset-notifications.sounds." + soundKey, ""));

        for (Player player : world.getPlayers()) {
            if (!zone.contains(player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ())) {
                continue;
            }
            player.sendMessage(message);
            if (useTitle) {
                player.sendTitle(title, subtitle,
                        plugin.getConfig().getInt("reset-notifications.title.fade-in", 10),
                        plugin.getConfig().getInt("reset-notifications.title.stay", 40),
                        plugin.getConfig().getInt("reset-notifications.title.fade-out", 10));
            }
            if (sound != null) {
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            }
        }
    }

    private void teleportPlayersOut(ZoneImpl zone) {
        World world = Bukkit.getWorld(zone.getWorldUID());
        if (world == null) {
            return;
        }

        Location fallback = safeSurface(zone);
        if (fallback == null) {
            return;
        }

        for (Player player : world.getPlayers()) {
            if (!zone.contains(player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ())) {
                continue;
            }
            scheduler.runSync(() -> player.teleport(fallback));
        }
    }

    private Location safeSurface(ZoneImpl zone) {
        World world = Bukkit.getWorld(zone.getWorldUID());
        if (world == null) {
            return null;
        }

        Location base = zone.getSpawn();
        if (base == null || !plugin.getConfig().getBoolean("teleport-on-reset.use-zone-spawn-if-set", true)) {
            int cx = (zone.getBounds().getMinX() + zone.getBounds().getMaxX()) / 2;
            int cz = (zone.getBounds().getMinZ() + zone.getBounds().getMaxZ()) / 2;
            base = new Location(world, cx + 0.5, world.getHighestBlockYAt(cx, cz) + 1, cz + 0.5);
        }

        if (!plugin.getConfig().getBoolean("teleport-on-reset.search-surface", true)) {
            return base;
        }

        int radius = Math.max(1, plugin.getConfig().getInt("teleport-on-reset.safety-radius", 3));
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = base.getBlockX() + dx;
                int z = base.getBlockZ() + dz;
                int y = world.getHighestBlockYAt(x, z) + 1;
                Block feet = world.getBlockAt(x, y, z);
                Block ground = world.getBlockAt(x, y - 1, z);
                if (feet.isPassable() && ground.getType().isSolid()) {
                    return new Location(world, x + 0.5, y, z + 0.5);
                }
            }
        }
        return base;
    }

    private void cancelReminder(String zoneId) {
        ZoneTask task = reminderTasks.remove(zoneId);
        if (task != null) {
            task.cancel();
        }
    }

    private Sound parseSound(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Sound.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
