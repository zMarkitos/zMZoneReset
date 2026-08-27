package dev.zm.zonereset.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.List;

public class PluginConfig {

    private final boolean debugMode;
    private final String language;
    private final int timerFormat;
    private final int maxBlocksPerTick;
    private final double maxMsptThreshold;
    private final boolean requireConfirmCommand;

    // countdown-announcements
    private final boolean countdownEnabled;
    private final List<Integer> announceBeforeSeconds;
    private final int lastCountdownSeconds;
    private final boolean countdownTitleEnabled;
    private final int countdownFadeIn;
    private final int countdownStay;
    private final int countdownFadeOut;
    private final String countdownSound;
    private final String countdownFinalSound;

    // progress-bar
    private final int progressBarLength;
    private final String progressFilledChar;
    private final String progressEmptyChar;
    private final String progressFilledColor;
    private final String progressEmptyColor;
    private final String progressBorderLeft;
    private final String progressBorderRight;

    public PluginConfig(ConfigurationSection section) {
        if (section == null) {
            this.debugMode = false;
            this.language = "ES";
            this.timerFormat = 3;
            this.maxBlocksPerTick = 1000;
            this.maxMsptThreshold = 45.0;
            this.requireConfirmCommand = false;
            this.countdownEnabled = true;
            this.announceBeforeSeconds = List.of(60, 30, 10);
            this.lastCountdownSeconds = 5;
            this.countdownTitleEnabled = true;
            this.countdownFadeIn = 5;
            this.countdownStay = 30;
            this.countdownFadeOut = 5;
            this.countdownSound = "BLOCK_NOTE_BLOCK_PLING";
            this.countdownFinalSound = "BLOCK_NOTE_BLOCK_BASS";
            this.progressBarLength = 20;
            this.progressFilledChar = "█";
            this.progressEmptyChar = "░";
            this.progressFilledColor = "#55FF55";
            this.progressEmptyColor = "#555555";
            this.progressBorderLeft = "&8[";
            this.progressBorderRight = "&8]";
        } else {
            this.debugMode = section.getBoolean("settings.debug", false);
            this.language = section.getString("settings.language", "ES");
            this.timerFormat = section.getInt("settings.timer-format", 3);
            this.maxBlocksPerTick = section.getInt("performance.max-time-per-tick-ms", 4) * 250;
            this.maxMsptThreshold = section.getDouble("performance.max-mspt-threshold", 45.0);
            this.requireConfirmCommand = section.getBoolean("settings.require-confirm-command", false);

            this.countdownEnabled = section.getBoolean("countdown-announcements.enabled", true);
            List<Integer> raw = section.getIntegerList("countdown-announcements.announce-before-seconds");
            this.announceBeforeSeconds = raw.isEmpty() ? List.of(60, 30, 10) : Collections.unmodifiableList(raw);
            this.lastCountdownSeconds = section.getInt("countdown-announcements.last-countdown-seconds", 5);
            this.countdownTitleEnabled = section.getBoolean("countdown-announcements.title.enabled", true);
            this.countdownFadeIn = section.getInt("countdown-announcements.title.fade-in", 5);
            this.countdownStay = section.getInt("countdown-announcements.title.stay", 30);
            this.countdownFadeOut = section.getInt("countdown-announcements.title.fade-out", 5);
            this.countdownSound = section.getString("countdown-announcements.sounds.countdown", "BLOCK_NOTE_BLOCK_PLING");
            this.countdownFinalSound = section.getString("countdown-announcements.sounds.final", "BLOCK_NOTE_BLOCK_BASS");
            this.progressBarLength = Math.max(1, section.getInt("progress-bar.length", 20));
            this.progressFilledChar = section.getString("progress-bar.filled-char", "█");
            this.progressEmptyChar = section.getString("progress-bar.empty-char", "░");
            this.progressFilledColor = section.getString("progress-bar.filled-color", "#55FF55");
            this.progressEmptyColor = section.getString("progress-bar.empty-color", "#555555");
            this.progressBorderLeft = section.getString("progress-bar.border-left", "&8[");
            this.progressBorderRight = section.getString("progress-bar.border-right", "&8]");
        }
    }

    public boolean isDebugMode() { return debugMode; }
    public String getLanguage() { return language; }
    public int getTimerFormat() { return timerFormat; }
    public int getMaxBlocksPerTick() { return maxBlocksPerTick; }
    public double getMaxMsptThreshold() { return maxMsptThreshold; }
    public boolean isRequireConfirmCommand() { return requireConfirmCommand; }

    public boolean isCountdownEnabled() { return countdownEnabled; }
    public List<Integer> getAnnounceBeforeSeconds() { return announceBeforeSeconds; }
    public int getLastCountdownSeconds() { return lastCountdownSeconds; }
    public boolean isCountdownTitleEnabled() { return countdownTitleEnabled; }
    public int getCountdownFadeIn() { return countdownFadeIn; }
    public int getCountdownStay() { return countdownStay; }
    public int getCountdownFadeOut() { return countdownFadeOut; }
    public String getCountdownSound() { return countdownSound; }
    public String getCountdownFinalSound() { return countdownFinalSound; }

    public int getProgressBarLength() { return progressBarLength; }
    public String getProgressFilledChar() { return progressFilledChar; }
    public String getProgressEmptyChar() { return progressEmptyChar; }
    public String getProgressFilledColor() { return progressFilledColor; }
    public String getProgressEmptyColor() { return progressEmptyColor; }
    public String getProgressBorderLeft() { return progressBorderLeft; }
    public String getProgressBorderRight() { return progressBorderRight; }
}
