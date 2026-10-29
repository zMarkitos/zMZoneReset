package dev.zm.zonereset.scheduler;

import dev.zm.zonereset.api.zone.Zone;
import dev.zm.zonereset.config.PluginConfig;
import dev.zm.zonereset.lang.LanguageManager;
import dev.zm.zonereset.reset.ResetManagerImpl;
import dev.zm.zonereset.zone.ZoneImpl;
import dev.zm.zonereset.zone.ZoneManagerImpl;
import dev.zm.zonereset.zone.ZoneStatus;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs every second (20 ticks). Decrements zone timers and:
 * 1. Fires the reset when the countdown reaches zero.
 * 2. Broadcasts global countdown announcements to ALL online players
 *    at configurable thresholds (e.g. 60s, 30s, 10s) and every
 *    second during the last N seconds.
 */
public class ZoneTimerTask implements Runnable {

    private final ZoneManagerImpl zoneManager;
    private final ResetManagerImpl resetManager;
    private final LanguageManager langManager;
    private final ZoneScheduler scheduler;
    /** Supplier so that config reloads are picked up automatically. */
    private final java.util.function.Supplier<PluginConfig> configSupplier;

    /**
     * Tracks which threshold (in seconds) has already been announced per zone,
     * so we never send the same threshold twice even if the timer drifts.
     * Cleared when a zone resets or the timer is restarted.
     */
    private final ConcurrentHashMap<String, Set<Integer>> announcedThresholds = new ConcurrentHashMap<>();

    /** Sound.valueOf() is relatively costly; resolve each configured name once. */
    private final ConcurrentHashMap<String, Sound> soundCache = new ConcurrentHashMap<>();
    private final Set<String> invalidSounds = ConcurrentHashMap.newKeySet();

    public ZoneTimerTask(ZoneManagerImpl zoneManager,
                         ResetManagerImpl resetManager,
                         LanguageManager langManager,
                         ZoneScheduler scheduler,
                         java.util.function.Supplier<PluginConfig> configSupplier) {
        this.zoneManager = Objects.requireNonNull(zoneManager, "zoneManager");
        this.resetManager = Objects.requireNonNull(resetManager, "resetManager");
        this.langManager = Objects.requireNonNull(langManager, "langManager");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
    }

    /**
     * Tracks the last seen remainingTicks per zone so we can detect when the timer
     * was reset externally (e.g., after a reset completes) and clear the announced set.
     */
    private final ConcurrentHashMap<String, Long> previousRemaining = new ConcurrentHashMap<>();

    /** Periodic cleanup of per-zone state for zones deleted at runtime. */
    private int pruneCounter = 0;

    @Override
    public void run() {
        Collection<Zone> zones = zoneManager.getAllZones();
        PluginConfig cfg = configSupplier.get();

        if (++pruneCounter >= 300) {
            pruneCounter = 0;
            announcedThresholds.keySet().removeIf(id -> !zoneManager.hasZone(id));
            previousRemaining.keySet().removeIf(id -> !zoneManager.hasZone(id));
        }

        for (Zone z : zones) {
            ZoneImpl zone = (ZoneImpl) z;

            if (zone.getStatus() != ZoneStatus.READY) {
                // If zone is resetting, clear previous so we detect the reset on next READY tick
                previousRemaining.remove(zone.getId());
                announcedThresholds.remove(zone.getId());
                continue;
            }

            if (zone.getResetIntervalTicks() <= 0) {
                previousRemaining.remove(zone.getId());
                announcedThresholds.remove(zone.getId());
                continue;
            }

            // Detect external timer reset (remainingTicks jumped back up significantly)
            long prevRemaining = previousRemaining.getOrDefault(zone.getId(), Long.MIN_VALUE);
            long beforeDecrement = zone.getRemainingTicks();
            if (prevRemaining != Long.MIN_VALUE && beforeDecrement > prevRemaining + 40) {
                // Timer was reset externally — clear announced set so next countdown fires normally
                announcedThresholds.remove(zone.getId());
            }

            long remaining = zone.decrementRemainingTicks(20);
            previousRemaining.put(zone.getId(), remaining);
            long remainingSeconds = remaining / 20;

            // — Countdown announcements —
            if (cfg != null && cfg.isCountdownEnabled()) {
                maybeAnnounce(zone, remainingSeconds, cfg);
            }

            // — Trigger reset —
            if (remaining <= 0) {
                // Clear tracked announcements so next cycle starts fresh
                announcedThresholds.remove(zone.getId());
                previousRemaining.remove(zone.getId());
                try {
                    resetManager.requestReset(zone);
                } catch (IllegalStateException ignored) {
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Announcement logic
    // -------------------------------------------------------------------------

    private void maybeAnnounce(ZoneImpl zone, long remainingSeconds, PluginConfig cfg) {
        // Which second-based thresholds are configured?
        List<Integer> thresholds = cfg.getAnnounceBeforeSeconds();
        int lastCountdown = Math.max(0, cfg.getLastCountdownSeconds());

        // Per-second countdown for the last N seconds (inclusive, e.g. 5..1)
        if (remainingSeconds > 0 && remainingSeconds <= lastCountdown) {
            if (shouldAnnounce(zone, (int) remainingSeconds)) {
                broadcastCountdown(zone, remainingSeconds, true, cfg);
            }
            return; // don't double-fire a threshold in this range
        }

        // Named thresholds (e.g. 60s, 30s, 10s) — only outside the last-countdown range
        for (int threshold : thresholds) {
            if (threshold <= lastCountdown) continue; // covered by per-second countdown
            if (remainingSeconds == threshold) {
                if (shouldAnnounce(zone, threshold)) {
                    broadcastCountdown(zone, remainingSeconds, false, cfg);
                }
                break;
            }
        }
    }

    /** Returns true if this (zone, seconds) pair has not been announced yet. */
    private boolean shouldAnnounce(ZoneImpl zone, long seconds) {
        Set<Integer> fired = announcedThresholds.computeIfAbsent(zone.getId(),
                k -> ConcurrentHashMap.newKeySet());
        return fired.add((int) seconds); // returns false if already contained
    }

    private void broadcastCountdown(ZoneImpl zone, long remainingSeconds, boolean isLastCountdown, PluginConfig cfg) {
        String timeStr = formatSeconds(remainingSeconds);
        String message = langManager.getMessage("countdown.message",
                "zone", zone.getId(),
                "time", timeStr);
        String titleText = langManager.getMessage("countdown.title",
                "zone", zone.getId(),
                "time", timeStr);
        String subtitleText = langManager.getMessage("countdown.subtitle",
                "zone", zone.getId(),
                "time", timeStr);

        boolean showTitle = cfg.isCountdownTitleEnabled();
        Sound sound = parseSound(isLastCountdown && remainingSeconds == 1
                ? cfg.getCountdownFinalSound()
                : cfg.getCountdownSound());
        float pitch = isLastCountdown && remainingSeconds == 1 ? 0.8f : 1.2f;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (scheduler.isFolia()) {
                // Titles/sounds touch player state, which belongs to the
                // player's region thread on Folia.
                int chunkX = player.getLocation().getBlockX() >> 4;
                int chunkZ = player.getLocation().getBlockZ() >> 4;
                scheduler.runOnChunk(player.getWorld(), chunkX, chunkZ,
                        () -> sendCountdown(player, message, titleText, subtitleText,
                                showTitle, sound, pitch, cfg));
            } else {
                sendCountdown(player, message, titleText, subtitleText,
                        showTitle, sound, pitch, cfg);
            }
        }
    }

    private void sendCountdown(Player player, String message, String title, String subtitle,
            boolean showTitle, Sound sound, float pitch, PluginConfig cfg) {
        if (!player.isOnline()) {
            return;
        }
        player.sendMessage(message);
        if (showTitle) {
            player.sendTitle(title, subtitle,
                    cfg.getCountdownFadeIn(),
                    cfg.getCountdownStay(),
                    cfg.getCountdownFadeOut());
        }
        if (sound != null) {
            player.playSound(player.getLocation(), sound, 1.0f, pitch);
        }
    }

    private String formatSeconds(long seconds) {
        if (seconds < 60) return seconds + "s";
        long mins = seconds / 60;
        long secs = seconds % 60;
        return secs > 0 ? mins + "m " + secs + "s" : mins + "m";
    }

    private Sound parseSound(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = name.trim().toUpperCase(Locale.ROOT);
        if (invalidSounds.contains(key)) {
            return null;
        }
        Sound cached = soundCache.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            Sound sound = Sound.valueOf(key);
            soundCache.put(key, sound);
            return sound;
        } catch (IllegalArgumentException e) {
            invalidSounds.add(key);
            return null;
        }
    }
}
