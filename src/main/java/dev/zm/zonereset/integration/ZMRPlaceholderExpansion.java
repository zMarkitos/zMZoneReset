package dev.zm.zonereset.integration;

import dev.zm.zonereset.api.ZoneManagerAPI;
import dev.zm.zonereset.api.zone.Zone;
import dev.zm.zonereset.config.PluginConfig;
import dev.zm.zonereset.lang.ColorParser;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * PlaceholderAPI expansion for zMZoneReset.
 *
 * <p>Available placeholders:
 * <ul>
 *   <li>%zmzonereset_status_&lt;zoneId&gt;%   – zone status (READY / RESETTING / DISABLED)</li>
 *   <li>%zmzonereset_strategy_&lt;zoneId&gt;% – reset strategy (AUTO / DIFF / SNAPSHOT)</li>
 *   <li>%zmzonereset_time_&lt;zoneId&gt;%     – formatted time remaining before next reset</li>
 *   <li>%zmzonereset_progress_&lt;zoneId&gt;% – coloured progress bar (elapsed / interval)</li>
 *   <li>%zmzonereset_progress_raw_&lt;zoneId&gt;% – raw 0–100 integer percentage</li>
 * </ul>
 */
public class ZMRPlaceholderExpansion extends PlaceholderExpansion {

    private final ZoneManagerAPI zoneManager;
    private final dev.zm.zonereset.config.ConfigManager configManager;
    private final String version;

    public ZMRPlaceholderExpansion(ZoneManagerAPI zoneManager,
                                    dev.zm.zonereset.config.ConfigManager configManager,
                                    String version) {
        this.zoneManager = zoneManager;
        this.configManager = configManager;
        this.version = version;
    }

    @Override
    public @NotNull String getIdentifier() { return "zmzonereset"; }

    @Override
    public @NotNull String getAuthor() { return "zMZoneReset"; }

    @Override
    public @NotNull String getVersion() { return version; }

    @Override
    public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {

        // %zmzonereset_status_<zoneId>%
        if (params.startsWith("status_")) {
            return zoneManager.getZone(params.substring("status_".length()))
                    .map(z -> z.getStatus().name())
                    .orElse("UNKNOWN");
        }

        // %zmzonereset_strategy_<zoneId>%
        if (params.startsWith("strategy_")) {
            return zoneManager.getZone(params.substring("strategy_".length()))
                    .map(z -> z.getResetStrategy().name())
                    .orElse("UNKNOWN");
        }

        // %zmzonereset_time_<zoneId>%
        if (params.startsWith("time_")) {
            Optional<Zone> opt = zoneManager.getZone(params.substring("time_".length()));
            if (opt.isEmpty()) return "UNKNOWN";
            Zone zone = opt.get();
            long remaining = Math.max(0L, zone.getRemainingTicks());
            int fmt = configManager.getConfig() != null ? configManager.getConfig().getTimerFormat() : 3;
            return dev.zm.zonereset.lang.TimeFormatter.format(remaining * 50L, fmt);
        }

        // %zmzonereset_progress_raw_<zoneId>%  →  integer 0-100
        if (params.startsWith("progress_raw_")) {
            Optional<Zone> opt = zoneManager.getZone(params.substring("progress_raw_".length()));
            if (opt.isEmpty()) return "0";
            return String.valueOf(calcPercent(opt.get()));
        }

        // %zmzonereset_progress_<zoneId>%  →  coloured progress bar
        if (params.startsWith("progress_")) {
            Optional<Zone> opt = zoneManager.getZone(params.substring("progress_".length()));
            if (opt.isEmpty()) return "UNKNOWN";
            return buildBar(opt.get());
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Progress bar helpers
    // -------------------------------------------------------------------------

    /**
     * Returns elapsed percentage 0–100:
     * 0 = just reset (full interval remaining), 100 = about to reset (0 remaining).
     */
    private int calcPercent(Zone zone) {
        long interval = zone.getResetIntervalTicks();
        if (interval <= 0) return 0;
        long remaining = Math.max(0L, zone.getRemainingTicks());
        long elapsed = interval - remaining;
        return (int) Math.min(100L, Math.max(0L, elapsed * 100L / interval));
    }

    /**
     * Builds a coloured progress bar string using config values.
     * Hex colours are rendered via {@link ColorParser}.
     *
     * <p>Example output (Minecraft chat): <code>&8[&a████████░░░░░░░░░░&8]</code>
     */
    private String buildBar(Zone zone) {
        PluginConfig cfg = configManager.getConfig();

        int length      = cfg != null ? cfg.getProgressBarLength()   : 20;
        String filled   = cfg != null ? cfg.getProgressFilledChar()  : "█";
        String empty    = cfg != null ? cfg.getProgressEmptyChar()   : "░";
        String fColor   = cfg != null ? cfg.getProgressFilledColor() : "#55FF55";
        String eColor   = cfg != null ? cfg.getProgressEmptyColor()  : "#555555";
        String bLeft    = cfg != null ? cfg.getProgressBorderLeft()  : "&8[";
        String bRight   = cfg != null ? cfg.getProgressBorderRight() : "&8]";

        int percent = calcPercent(zone);
        int filledCount = (int) Math.round(length * percent / 100.0);
        int emptyCount  = length - filledCount;

        // Build filled segment
        String filledSegment = toColorCode(fColor) + filled.repeat(Math.max(0, filledCount));
        // Build empty segment
        String emptySegment  = toColorCode(eColor) + empty.repeat(Math.max(0, emptyCount));

        String bar = bLeft + filledSegment + emptySegment + bRight;
        return ColorParser.parse(bar);
    }

    /**
     * Converts a color value to a chat color code string.
     * Accepts:
     * <ul>
     *   <li>{@code #RRGGBB} hex → {@code &#RRGGBB} (parsed by ColorParser)</li>
     *   <li>{@code &x} legacy codes → passed through as-is</li>
     * </ul>
     */
    private String toColorCode(String color) {
        if (color == null || color.isBlank()) return "";
        String trimmed = color.trim();
        // Bare hex #RRGGBB → prefix with &
        if (trimmed.startsWith("#") && trimmed.length() == 7) {
            return "&#" + trimmed.substring(1);
        }
        return trimmed;
    }
}
