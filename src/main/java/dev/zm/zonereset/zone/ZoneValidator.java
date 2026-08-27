package dev.zm.zonereset.zone;

import dev.zm.zonereset.core.logging.ZoneLogger;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.*;

public final class ZoneValidator {

    private static final int MAX_ID_LENGTH = 64;

    private static final String VALID_ID_PATTERN = "^[a-zA-Z0-9_\\-]+$";

    private static final long MAX_VOLUME = 300L * 300L * 384L;

    private final ZoneLogger logger;

    public ZoneValidator(ZoneLogger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public List<String> validateId(String id) {
        List<String> errors = new ArrayList<>();

        if (id == null || id.isBlank()) {
            errors.add("El ID de zona no puede estar vacío.");
            return errors;
        }

        String stripped = id.strip();

        if (stripped.length() > MAX_ID_LENGTH) {
            errors.add("El ID '" + stripped + "' es demasiado largo (máx. " + MAX_ID_LENGTH + " caracteres).");
        }

        if (!stripped.matches(VALID_ID_PATTERN)) {
            errors.add("El ID '" + stripped + "' contiene caracteres no permitidos. " +
                    "Solo se permiten letras, números, guiones y guiones bajos.");
        }

        return errors;
    }

    public List<String> validateWorld(String worldName) {
        List<String> errors = new ArrayList<>();

        if (worldName == null || worldName.isBlank()) {
            errors.add("El nombre del mundo no puede estar vacío.");
            return errors;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            errors.add("El mundo '" + worldName + "' no existe o no está cargado. " +
                    "Mundos disponibles: " + getLoadedWorldNames());
        }

        return errors;
    }

    public List<String> validateWorldUID(UUID worldUID) {
        List<String> errors = new ArrayList<>();

        if (worldUID == null) {
            errors.add("El UUID del mundo no puede ser nulo.");
            return errors;
        }

        World world = Bukkit.getWorld(worldUID);
        if (world == null) {
            errors.add("El mundo con UUID '" + worldUID + "' no está cargado. " +
                    "Puede que el mundo haya sido descargado o eliminado.");
        }

        return errors;
    }

    public List<String> validateBounds(UUID worldUID,
            int x1, int y1, int z1,
            int x2, int y2, int z2) {
        List<String> errors = new ArrayList<>();

        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);

        if (worldUID != null) {
            World world = Bukkit.getWorld(worldUID);
            if (world != null) {
                int worldMinY = world.getMinHeight();
                int worldMaxY = world.getMaxHeight() - 1;

                if (minY < worldMinY) {
                    errors.add("La coordenada Y mínima (" + minY + ") está por debajo del límite " +
                            "del mundo '" + world.getName() + "' (" + worldMinY + ").");
                }
                if (maxY > worldMaxY) {
                    errors.add("La coordenada Y máxima (" + maxY + ") supera el límite " +
                            "del mundo '" + world.getName() + "' (" + worldMaxY + ").");
                }
            }
        }

        if (x1 == x2 && y1 == y2 && z1 == z2) {
            errors.add("Las dos esquinas de la zona son idénticas. " +
                    "La zona debe tener al menos 1 bloque de extensión en algún eje.");
        }

        ZoneBounds tempBounds = new ZoneBounds(x1, y1, z1, x2, y2, z2);
        long volume = tempBounds.getVolume();
        if (volume > MAX_VOLUME) {
            errors.add(String.format(
                    "El volumen de la zona (%,d bloques) supera el máximo permitido (%,d bloques). " +
                            "Considere dividir la zona en zonas más pequeñas.",
                    volume, MAX_VOLUME));
        }

        return errors;
    }

    public long parseResetInterval(String intervalStr) {
        if (intervalStr == null || intervalStr.isBlank()) {
            return -1L;
        }

        String s = intervalStr.strip().toLowerCase();

        if (s.equals("-1") || s.equals("disabled") || s.equals("never")) {
            return -1L;
        }

        try {
            if (s.endsWith("h")) {
                long hours = Long.parseLong(s.substring(0, s.length() - 1));
                return hours * 72_000L;
            } else if (s.endsWith("m")) {
                long minutes = Long.parseLong(s.substring(0, s.length() - 1));
                return minutes * 1_200L;
            } else if (s.endsWith("s")) {
                long seconds = Long.parseLong(s.substring(0, s.length() - 1));
                return seconds * 20L;
            } else {
                long ticks = Long.parseLong(s);
                if (ticks < 0)
                    return -1L;
                if (ticks > 0 && ticks < 200) {
                    logger.warn("Intervalo de reset muy corto ({} ticks = {}s). " +
                            "Se recomienda al menos 200 ticks (10s).", ticks, ticks / 20);
                }
                return ticks;
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Formato de intervalo inválido: '" + intervalStr + "'. " +
                            "Use formatos como: 5m, 30s, 1h, 6000 (ticks), -1 (deshabilitado).");
        }
    }

    public List<String> validateBounds(ZoneBounds bounds, UUID worldUID) {
        return validateBounds(worldUID,
                bounds.getMinX(), bounds.getMinY(), bounds.getMinZ(),
                bounds.getMaxX(), bounds.getMaxY(), bounds.getMaxZ());
    }

    private static String getLoadedWorldNames() {
        List<String> names = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) {
            names.add(w.getName());
        }
        return String.join(", ", names);
    }
}
