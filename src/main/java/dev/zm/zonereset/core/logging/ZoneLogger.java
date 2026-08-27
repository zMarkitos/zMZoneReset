// file: ZoneLogger.java
// description: Wrapper for Bukkit's logger with debug toggle and formatted messages (supports {} placeholders).

package dev.zm.zonereset.core.logging;

import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ZoneLogger {

    private final Logger logger;
    private volatile boolean debugEnabled;

    public ZoneLogger(Plugin plugin) {
        this.logger = Objects.requireNonNull(plugin, "plugin").getLogger();
        this.debugEnabled = false;
    }

    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public void info(String message, Object... args) {
        logger.info(format(message, args));
    }

    public void debug(String message, Object... args) {
        if (debugEnabled) {
            logger.info("[DEBUG] " + format(message, args));
        }
    }

    public void warn(String message, Object... args) {
        logger.warning(format(message, args));
    }

    public void warn(String message, Throwable cause, Object... args) {
        logger.log(Level.WARNING, format(message, args), cause);
    }

    public void error(String message, Object... args) {
        logger.severe(format(message, args));
    }

    public void error(String message, Throwable cause, Object... args) {
        logger.log(Level.SEVERE, format(message, args), cause);
    }

    private static String format(String message, Object[] args) {
        if (args == null || args.length == 0) {
            return message;
        }
        StringBuilder sb = new StringBuilder(message.length() + 64);
        int argIdx = 0;
        int i = 0;
        while (i < message.length()) {
            char c = message.charAt(i);
            if (c == '{' && i + 1 < message.length() && message.charAt(i + 1) == '}') {
                if (argIdx < args.length && !(args[argIdx] instanceof Throwable)) {
                    sb.append(args[argIdx++]);
                }
                i += 2;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }
}