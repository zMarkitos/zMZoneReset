// file: TimeFormatter.java
// description: Formats milliseconds into human-readable time strings (HH:MM:SS, short, or detailed).

package dev.zm.zonereset.lang;

import java.util.concurrent.TimeUnit;

public final class TimeFormatter {

    private TimeFormatter() {
    }

    public static String format(long millis, int format) {
        if (millis <= 0)
            return format == 1 ? "00:00:00" : "0s";

        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;

        switch (format) {
            case 1:
                return String.format("%02d:%02d:%02d", hours, minutes, seconds);
            case 2:
                if (hours > 0)
                    return hours + "h " + minutes + "m";
                if (minutes > 0)
                    return minutes + "m " + seconds + "s";
                return seconds + "s";
            case 3:
            default:
                if (hours > 0)
                    return String.format("%02dh %02dm %02ds", hours, minutes, seconds);
                if (minutes > 0)
                    return String.format("%02dm %02ds", minutes, seconds);
                return String.format("%02ds", seconds);
        }
    }
}